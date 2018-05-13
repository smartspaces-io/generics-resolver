package ru.vyarus.java.generics.resolver.inlying

import ru.vyarus.java.generics.resolver.GenericsResolver
import ru.vyarus.java.generics.resolver.context.GenericsContext
import ru.vyarus.java.generics.resolver.inlying.support.track.ArrayLeft
import ru.vyarus.java.generics.resolver.inlying.support.track.NestedLeft
import ru.vyarus.java.generics.resolver.inlying.support.track.NestedRight
import ru.vyarus.java.generics.resolver.inlying.support.track.RootScopeContradiction
import ru.vyarus.java.generics.resolver.inlying.support.track.RootScopeContradiction2
import ru.vyarus.java.generics.resolver.inlying.support.track.Source
import ru.vyarus.java.generics.resolver.inlying.support.track.Nested
import ru.vyarus.java.generics.resolver.inlying.support.track.ContradictionTrack
import ru.vyarus.java.generics.resolver.inlying.support.track.MultiTier
import ru.vyarus.java.generics.resolver.inlying.support.track.MultipleGenerics
import ru.vyarus.java.generics.resolver.inlying.support.track.NoTrack
import ru.vyarus.java.generics.resolver.inlying.support.track.Target
import ru.vyarus.java.generics.resolver.inlying.support.track.Wildcard
import ru.vyarus.java.generics.resolver.inlying.support.track.WildcardDeclaration
import ru.vyarus.java.generics.resolver.inlying.support.track.WildcardDeclaration2
import ru.vyarus.java.generics.resolver.error.GenericsTrackingException
import spock.lang.Specification

/**
 * @author Vyacheslav Rusakov
 * @since 09.05.2018
 */
class GenericBacktrackingTest extends Specification {

    def "Check generics tracking cases"() {

        setup: "prepare base type context"
        GenericsContext context = GenericsResolver.resolve(Source)

        when: "multiple generics"
        def res = context.inlyingFieldTypeAs(Source.getDeclaredField("target"), MultipleGenerics)
        then: "one generic tracked"
        res.generic("A") == String
        res.generic("B") == Object

        when: "no track"
        res = context.inlyingFieldTypeAs(Source.getDeclaredField("target"), NoTrack)
        then: "nothing to track"
        res.generic("P") == Object
        and: "actual hierarchy generic was Object, but known generic was used"
        res.type(Target).generic("T") == String

        when: "known generic contradict actual generic value in class hierarchy"
        context.inlyingFieldTypeAs(Source.getDeclaredField("target"), ContradictionTrack)
        then: "contradiction detected"
        def ex = thrown(GenericsTrackingException)
        ex.message == "Failed to track generics of ContradictionTrack<P> from sub type Target<String>"
        ex.getCause().message == "Known generic T of Target<T> is not compatible with ContradictionTrack hierarchy: String when required Integer"

        when: "multiple tiers before known type"
        res = context.inlyingFieldTypeAs(Source.getDeclaredField("target"), MultiTier)
        then: "one generic tracked"
        res.generic("A") == String
        res.generic("B") == Object

    }

    def "Complex checks"() {

        when: "nested declaration"
        GenericsContext context = GenericsResolver.resolve(Nested)
        def res = context.inlyingFieldTypeAs(Nested.getDeclaredField("target"), Nested)
        then: "one generic tracked"
        res.genericAsString("K") == 'String'

        when: "nested declaration with non equal right"
        context = GenericsResolver.resolve(NestedRight)
        res = context.inlyingFieldTypeAs(NestedRight.getDeclaredField("target"), NestedRight)
        then: "one generic tracked"
        res.genericAsString("K") == 'String'

        when: "nested declaration with non equal left"
        context = GenericsResolver.resolve(NestedLeft)
        res = context.inlyingFieldTypeAs(NestedLeft.getDeclaredField("target"), NestedLeft)
        then: "one generic tracked"
        res.genericAsString("K") == 'String'

        when: "known generic is a wildcard"
        context = GenericsResolver.resolve(Wildcard)
        res = context.inlyingFieldTypeAs(Wildcard.getDeclaredField("target"), Wildcard)
        then: "one generic tracked"
        res.genericAsString("K") == 'String'

        when: "transitive declaration with wildcard"
        context = GenericsResolver.resolve(WildcardDeclaration)
        res = context.inlyingFieldTypeAs(WildcardDeclaration.getDeclaredField("target"), WildcardDeclaration)
        then: "one generic tracked"
        res.genericAsString("B") == 'String'
        res.genericAsString("A") == 'Object'

        when: "transitive declaration with wildcard 2"
        context = GenericsResolver.resolve(WildcardDeclaration2)
        res = context.inlyingFieldTypeAs(WildcardDeclaration2.getDeclaredField("target"), WildcardDeclaration2)
        then: "wildcard generic aligned to base type"
        res.genericAsString("B") == 'String'
        res.genericAsString("A") == 'String'


        when: "array generic"
        context = GenericsResolver.resolve(ArrayLeft)
        res = context.inlyingFieldTypeAs(ArrayLeft.getDeclaredField("target"), ArrayLeft)
        then: "resovled"
        res.genericAsString("A") == 'String'

        when: "root scope contradicts with known generic"
        context = GenericsResolver.resolve(RootScopeContradiction)
        context.inlyingFieldTypeAs(RootScopeContradiction.getDeclaredField("target"), RootScopeContradiction)
        then: "error"
        def ex = thrown(GenericsTrackingException)
        ex.message == "Failed to track generics of RootScopeContradiction<T> from sub type Target<String>"
        ex.getCause().message == "Known generic T of Target<T> is not compatible with RootScopeContradiction hierarchy: String when required Integer"

        when: "root scope contradicts with known generic"
        context = GenericsResolver.resolve(RootScopeContradiction2)
        context.inlyingFieldTypeAs(RootScopeContradiction2.getDeclaredField("target"), RootScopeContradiction2)
        then: "error"
        ex = thrown(GenericsTrackingException)
        ex.message == "Failed to track generics of RootScopeContradiction2<T, K> from sub type Target<String>"
        ex.getCause().message == "Known generic T of Target<T> is not compatible with RootScopeContradiction2 hierarchy: String when required Integer"
    }
}
