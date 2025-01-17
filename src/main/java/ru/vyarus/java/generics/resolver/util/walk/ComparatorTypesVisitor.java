package ru.vyarus.java.generics.resolver.util.walk;

import ru.vyarus.java.generics.resolver.util.GenericsUtils;
import ru.vyarus.java.generics.resolver.util.TypeUtils;
import ru.vyarus.java.generics.resolver.util.map.IgnoreGenericsMap;

import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

/**
 * Check if one type is more specific. Stops when first type is not more specific.
 * Implicitly tracks compatibility: check {@link #isCompatible()} before
 * calling {@link #isMoreSpecific()} as the later always assume compatible hierarchies.
 * For example, {@code ArrayList<String>} is more specific then {@code List<Integer>}, but not compatible.
 * <p>
 * Note that specificity is not the same as assignability. For example {@code List} is assignable to
 * {@code List<? extends String>}, but later is more specific (actually). Other example is unknown types:
 * {@code T<String, Object>} is assignable to {@code T<String, String>}, but later is more specific
 * (contains more type information).
 *
 * @author Vyacheslav Rusakov
 * @see TypesWalker
 * @since 11.05.2018
 */
public class ComparatorTypesVisitor implements TypesVisitor {

    private static final IgnoreGenericsMap IGNORE = IgnoreGenericsMap.getInstance();

    private boolean compatible = true;
    private boolean moreSpecific = true;
    // note: it is not possible to track specificity flag without equality, because we don't know what types pair
    // will be the last one. All we can is to track equality through all layers and look it in final getter
    private boolean equal = true;

    @Override
    public boolean next(final Type one, final Type two) {
        // when right part is object consider left as more specific: in edge case, Object is more specific then Object
        if (two != Object.class) {
            // upper bounds: single element for most case and only for repackaged type declarations
            // (? extends A & B) could be more types
            final Class[] oneBounds = GenericsUtils.resolveUpperBounds(one, IGNORE);
            final Class[] twoBounds = GenericsUtils.resolveUpperBounds(two, IGNORE);

            final boolean boundsEqual = Arrays.equals(oneBounds, twoBounds);

            if (!boundsEqual) {
                // direct comparison for non equal objects
                // edge case: Object on the left could not be more specific
                moreSpecific = oneBounds[0] != Object.class && TypeUtils.isAssignableBounds(oneBounds, twoBounds);
            }

            // check lower bounds when upper bounds are equal (checked above)
            moreSpecific = moreSpecific && compareLowerBounds(one, two);

            // track equality 
            equal = equal && boundsEqual;

            // general specificity might be already obvious, but generics could differ and this must be checked
            // in order to throw incompatible types
        } else {
            moreSpecific = moreSpecific && one != Object.class;
            equal = equal && one == Object.class;
        }
        return true;
    }

    @Override
    public void incompatibleHierarchy(final Type one, final Type two) {
        compatible = false;
        equal = false;
        moreSpecific = false;
    }

    /**
     * @return true if first type is more specific, false otherwise (including when types are equal)
     */
    public boolean isMoreSpecific() {
        // more specific flag may be true in case when all layers are equal, so it is important
        // to use both flags to deny equal types
        return moreSpecific && !equal;
    }

    /**
     * Note that incoming types could be not directly equal but equal actually (e.g. {@code List} and
     * {@code List<? extends Object>}).
     *
     * @return true when types are equal
     */
    public boolean isEqual() {
        return equal;
    }

    /**
     * @return true when types are incompatible, false when compatible
     */
    public boolean isCompatible() {
        return compatible;
    }

    /**
     * Check lower bounded wildcard cases. Method is not called if upper bounds are not equal.
     * <p>
     * If right is not lower wildcard - no matter what left is - it's always more specific.
     * <p>
     * If right is lower wildcard and left is simple class then right could be more specific only when left is Object.
     * Note that in this case left could be not Object as any type is more specific then Object (lower wildcard upper
     * bound).
     * <p>
     * When both lower wildcards: lower bounds must be from one hierarchy and left type should be lower.
     * For example,  ? super Integer and ? super BigInteger are not assignable in spite of the fact that they
     * share some common types. ? super Number is more specific then ? super Integer (super inverse meaning).
     *
     * @param one first type
     * @param two second type
     * @return true when left is more specific, false otherwise
     */
    private boolean compareLowerBounds(final Type one, final Type two) {
        final boolean res;
        // ? super Object is impossible here due to types cleanup in tree walker
        if (notLowerBounded(two)) {
            // ignore possible left lower bound when simple type on the right (not Object - root condition above)
            res = true;
            // if left type is wildcard they are not equal (super Object case already prevented)
            equal = equal && (notLowerBounded(one) || ((WildcardType) one).getLowerBounds()[0] == Object.class);
        } else if (notLowerBounded(one)) {
            // special case: left is Object and right is lower bounded wildcard
            // e.g Object and ? super String (last is more specific, while it's upper bound is Object too)
            // otherwise, any non Object is more specific
            res = one != Object.class;
            // two is lower bounded and one is not - can't be equal (super Object case already prevented)
            equal = equal && ((WildcardType) two).getLowerBounds()[0] == Object.class;
        } else {
            final Type lowerOne = ((WildcardType) two).getLowerBounds()[0];
            final Type lowerTwo = ((WildcardType) one).getLowerBounds()[0];
            equal = equal && lowerOne.equals(lowerTwo);

            // left type's bound must be lower: not a mistake! left (super inversion)!
            res = !equal && TypeUtils.isMoreSpecific(
                    lowerOne,
                    lowerTwo);
        }
        return res;
    }

    private boolean notLowerBounded(final Type type) {
        return !(type instanceof WildcardType) || ((WildcardType) type).getLowerBounds().length == 0;
    }
}
