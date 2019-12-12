/*
 * Copyright 2019, Backblaze Inc. All Rights Reserved.
 * License https://www.backblaze.com/using_b2_code.html
 */
package com.backblaze.b2.json;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TypeResolverTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private final TypeResolver resolverWithoutTypeArguments = new TypeResolver(TestClassWithoutTypeArguments.class);
    private final Field[] declaredFieldsWithoutTypeArguments = TestClassWithoutTypeArguments.class.getDeclaredFields();
    private final TypeResolver resolverWithTypeArguments = new TypeResolver(
            TestClassWithTypeArguments.class,
            new Type[]{String.class, Integer.class});
    private final Field[] declaredFieldsWithTypeArguments = TestClassWithTypeArguments.class.getDeclaredFields();


    @Test
    public void testNoActualTypeArguments_resolvePrimitives() {
        // Make sure we can still resolve concrete primitives, they don't have any type parameters.
        assertEquals(int.class, resolverWithoutTypeArguments.resolveType(declaredFieldsWithoutTypeArguments[0]));
    }

    @Test
    public void testGetType() {
        {
            final Type resolvedType = resolverWithoutTypeArguments.getType();
            assertTrue(resolvedType instanceof Class);
            assertEquals(TestClassWithoutTypeArguments.class, resolvedType);
        }

        {
            final Type resolvedType = resolverWithTypeArguments.getType();
            assertTrue(resolvedType instanceof TypeResolver.ResolvedParameterizedType);
            final ParameterizedType resolvedParameterizedType = (ParameterizedType) resolvedType;
            assertEquals(TestClassWithTypeArguments.class, resolvedParameterizedType.getRawType());
            assertArrayEquals(
                    new Type[]{String.class, Integer.class},
                    resolvedParameterizedType.getActualTypeArguments());
        }
    }

    @Test
    public void testConstructTypeResolverWithoutTypeArguments_throws() {
        thrown.expect(RuntimeException.class);
        thrown.expectMessage("actualTypeArguments must be same length as class' type parameters");
        new TypeResolver(TestClassWithTypeArguments.class);
    }

    @Test
    public void testActualTypeArguments_resolvePrimitives() {
        assertEquals(int.class, resolverWithTypeArguments.resolveType(declaredFieldsWithTypeArguments[0]));
    }

    @Test
    public void testActualTypeArguments_resolveTypeVariables() {
        assertEquals(String.class, resolverWithTypeArguments.resolveType(declaredFieldsWithTypeArguments[1]));
        assertEquals(Integer.class, resolverWithTypeArguments.resolveType(declaredFieldsWithTypeArguments[2]));
    }

    @Test
    public void testActualTypeArguments_resolveParameterizedType_oneType() {
        final Type resolvedType = resolverWithTypeArguments.resolveType(declaredFieldsWithTypeArguments[3]);
        assertTrue(resolvedType instanceof ParameterizedType);
        final ParameterizedType resolvedParameterizedType = (ParameterizedType) resolvedType;
        assertEquals(OneParameterizedType.class, resolvedParameterizedType.getRawType());
        assertArrayEquals(new Type[]{String.class}, resolvedParameterizedType.getActualTypeArguments());
    }

    @Test
    public void testActualTypeArguments_resolveParameterizedType_twoTypes() {
        final Type resolvedType = resolverWithTypeArguments.resolveType(declaredFieldsWithTypeArguments[4]);
        assertTrue(resolvedType instanceof ParameterizedType);
        final ParameterizedType resolvedParameterizedType = (ParameterizedType) resolvedType;
        assertEquals(TwoParameterizedTypes.class, resolvedParameterizedType.getRawType());
        assertArrayEquals(new Type[]{String.class, Integer.class}, resolvedParameterizedType.getActualTypeArguments());
    }

    @Test
    public void testActualTypeArguments_resolveParameterizedType_nestedParameterizedTypes() {
        final Type resolvedType = resolverWithTypeArguments.resolveType(declaredFieldsWithTypeArguments[5]);
        assertTrue(resolvedType instanceof ParameterizedType);
        final ParameterizedType resolvedParameterizedType = (ParameterizedType) resolvedType;
        assertEquals(OneParameterizedType.class, resolvedParameterizedType.getRawType());
        assertArrayEquals(
                new Type[]{
                        new TypeResolver.ResolvedParameterizedType(
                                TwoParameterizedTypes.class,
                                new Type[]{Integer.class, String.class}
                        )
                }, resolvedParameterizedType.getActualTypeArguments());

        final Type[] resolvedActualTypeArguments = resolvedParameterizedType.getActualTypeArguments();
        assertEquals(1, resolvedActualTypeArguments.length);
        assertTrue(resolvedActualTypeArguments[0] instanceof TypeResolver.ResolvedParameterizedType);
        final TypeResolver.ResolvedParameterizedType resolvedParameterizedType_nested = (TypeResolver.ResolvedParameterizedType) resolvedActualTypeArguments[0];
        assertEquals(TwoParameterizedTypes.class, resolvedParameterizedType_nested.getRawType());
        assertArrayEquals(new Type[]{Integer.class, String.class}, resolvedParameterizedType_nested.getActualTypeArguments());
    }

    @Test
    public void testActualTypeTypeArguments_array() {
        final Type resolvedType = resolverWithTypeArguments.resolveType(declaredFieldsWithTypeArguments[6]);
        assertTrue(resolvedType instanceof GenericArrayType);
        final GenericArrayType resolvedGenericArrayType = (GenericArrayType) resolvedType;
        assertEquals(String.class, resolvedGenericArrayType.getGenericComponentType());
    }

    @Test
    public void testActualTypeTypeArguments_arrayOfNestedTypes() {
        final Type resolvedType = resolverWithTypeArguments.resolveType(declaredFieldsWithTypeArguments[7]);
        assertTrue(resolvedType instanceof GenericArrayType);
        final GenericArrayType resolvedGenericArrayType = (GenericArrayType) resolvedType;
        assertEquals(
                new TypeResolver.ResolvedParameterizedType(
                        OneParameterizedType.class,
                        new Type[]{Integer.class}
                ),
                resolvedGenericArrayType.getGenericComponentType());
    }

    @Test
    public void testRecursiveGenericClass() {
        final TypeResolver resolveWithRecursiveGenericClass = new TypeResolver(
                RecursiveClass.class,
                new Type[]{String.class});

        final Type resolvedField0 = resolveWithRecursiveGenericClass.resolveType(
                resolveWithRecursiveGenericClass.getDeclaredFields()[0]);
        assertEquals(String.class, resolvedField0);

        final Type resolvedField1 = resolveWithRecursiveGenericClass.resolveType(
                resolveWithRecursiveGenericClass.getDeclaredFields()[1]);
        assertEquals(
                new TypeResolver.ResolvedParameterizedType(
                        RecursiveClass.class,
                        new Type[]{String.class}
                ), resolvedField1);
    }

    @Test
    public void testWildcardTypesNotSupported() {
        final TypeResolver resolverWithWildcards = new TypeResolver(
                EnclosingWithWildcards.class,
                new Type[]{String.class});

        thrown.expect(RuntimeException.class);
        thrown.expectMessage("Wildcard types are not supported");
        resolverWithWildcards.resolveType(resolverWithWildcards.getDeclaredFields()[0]);
    }

    @Test
    public void testActualTypeArgumentsWrongLength_throws() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("actualTypeArguments must be same length as class' type parameters");
        new TypeResolver(TestClassWithTypeArguments.class, new Type[]{});
    }

    @Test
    public void testResolveFieldFromDifferentClass_throws() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("cannot resolve fields from other classes");
        resolverWithTypeArguments.resolveType(EnclosingWithWildcards.class.getDeclaredFields()[0]);
    }

    @Test
    public void testResolvedTypeEquality() {
        final TypeResolver.ResolvedParameterizedType resolvedParameterizedType =
                new TypeResolver.ResolvedParameterizedType(OneParameterizedType.class, new Type[]{Integer.class});
        final TypeResolver.ResolvedParameterizedType resolvedParameterizedTypeEqual =
                new TypeResolver.ResolvedParameterizedType(OneParameterizedType.class, new Type[]{Integer.class});
        final TypeResolver.ResolvedParameterizedType resolvedParameterizedTypeNotEqual1 =
                new TypeResolver.ResolvedParameterizedType(OneParameterizedType.class, new Type[]{String.class});
        final TypeResolver.ResolvedParameterizedType resolvedParameterizedTypeNotEqual2 =
                new TypeResolver.ResolvedParameterizedType(List.class, new Type[]{Integer.class});

        assertEquals(resolvedParameterizedType, resolvedParameterizedTypeEqual);
        assertEquals(resolvedParameterizedType.hashCode(), resolvedParameterizedTypeEqual.hashCode());

        assertNotEquals(resolvedParameterizedType, resolvedParameterizedTypeNotEqual1);
        assertNotEquals(resolvedParameterizedType.hashCode(), resolvedParameterizedTypeNotEqual1.hashCode());

        assertNotEquals(resolvedParameterizedType, resolvedParameterizedTypeNotEqual2);
        assertNotEquals(resolvedParameterizedType.hashCode(), resolvedParameterizedTypeNotEqual2.hashCode());

        final TypeResolver.ResolvedGenericArrayType resolvedGenericArrayType =
                new TypeResolver.ResolvedGenericArrayType(String.class);
        final TypeResolver.ResolvedGenericArrayType resolvedGenericArrayTypeEqual =
                new TypeResolver.ResolvedGenericArrayType(String.class);
        final TypeResolver.ResolvedGenericArrayType resolvedGenericArrayTypeNotEqual =
                new TypeResolver.ResolvedGenericArrayType(Integer.class);

        assertEquals(resolvedGenericArrayType, resolvedGenericArrayTypeEqual);
        assertEquals(resolvedGenericArrayType.hashCode(), resolvedGenericArrayTypeEqual.hashCode());

        assertNotEquals(resolvedGenericArrayType, resolvedGenericArrayTypeNotEqual);
        assertNotEquals(resolvedGenericArrayType.hashCode(), resolvedGenericArrayTypeNotEqual.hashCode());
    }

    // Class definitions below.

    private static class TestClassWithoutTypeArguments {
        private int intType;
    }

    private static class TestClassWithTypeArguments<T, U> {
        private int intType;
        private T tType;
        private U uType;
        private OneParameterizedType<T> nestedTType;
        private TwoParameterizedTypes<T, U> nestedTUTypes;
        private OneParameterizedType<TwoParameterizedTypes<U, T>> doubleNestedTypes;
        private T[] tArray;
        private OneParameterizedType<U>[] arrayOfNestedUTypes;
    }

    private static class OneParameterizedType<C> {
        private C value;
    }

    private static class TwoParameterizedTypes<A, B> {
        private A value1;
        private B value2;
    }

    private static class RecursiveClass<T> {
        private T data;
        private RecursiveClass<T> next;
    }

    private static class EnclosingWithWildcards<T> {
        private List<? extends T> listWithWildcards;
    }

}