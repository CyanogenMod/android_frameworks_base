/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.layoutlib.create;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.layoutlib.create.dataclass.ClassWithNative;
import com.android.tools.layoutlib.create.dataclass.OuterClass;
import com.android.tools.layoutlib.create.dataclass.OuterClass.InnerClass;

import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class DelegateClassAdapterTest {

    private MockLog mLog;

    private static final String NATIVE_CLASS_NAME = ClassWithNative.class.getCanonicalName();
    private static final String OUTER_CLASS_NAME = OuterClass.class.getCanonicalName();
    private static final String INNER_CLASS_NAME = OuterClass.class.getCanonicalName() + "$" +
                                                   InnerClass.class.getSimpleName();

    @Before
    public void setUp() throws Exception {
        mLog = new MockLog();
        mLog.setVerbose(true); // capture debug error too
    }

    /**
     * Tests that a class not being modified still works.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testNoOp() throws Throwable {
        // create an instance of the class that will be modified
        // (load the class in a distinct class loader so that we can trash its definition later)
        ClassLoader cl1 = new ClassLoader(this.getClass().getClassLoader()) { };
        Class<ClassWithNative> clazz1 = (Class<ClassWithNative>) cl1.loadClass(NATIVE_CLASS_NAME);
        ClassWithNative instance1 = clazz1.newInstance();
        assertEquals(42, instance1.add(20, 22));
        try {
            instance1.callNativeInstance(10, 3.1415, new Object[0] );
            fail("Test should have failed to invoke callTheNativeMethod [1]");
        } catch (UnsatisfiedLinkError e) {
            // This is expected to fail since the native method is not implemented.
        }

        // Now process it but tell the delegate to not modify any method
        ClassWriter cw = new ClassWriter(0 /*flags*/);

        HashSet<String> delegateMethods = new HashSet<String>();
        String internalClassName = NATIVE_CLASS_NAME.replace('.', '/');
        DelegateClassAdapter cv = new DelegateClassAdapter(
                mLog, cw, internalClassName, delegateMethods);

        ClassReader cr = new ClassReader(NATIVE_CLASS_NAME);
        cr.accept(cv, 0 /* flags */);

        // Load the generated class in a different class loader and try it again

        ClassLoader2 cl2 = null;
        try {
            cl2 = new ClassLoader2() {
                @Override
                public void testModifiedInstance() throws Exception {
                    Class<?> clazz2 = loadClass(NATIVE_CLASS_NAME);
                    Object i2 = clazz2.newInstance();
                    assertNotNull(i2);
                    assertEquals(42, callAdd(i2, 20, 22));

                    try {
                        callCallNativeInstance(i2, 10, 3.1415, new Object[0]);
                        fail("Test should have failed to invoke callTheNativeMethod [2]");
                    } catch (InvocationTargetException e) {
                        // This is expected to fail since the native method has NOT been
                        // overridden here.
                        assertEquals(UnsatisfiedLinkError.class, e.getCause().getClass());
                    }

                    // Check that the native method does NOT have the new annotation
                    Method[] m = clazz2.getDeclaredMethods();
                    assertEquals("native_instance", m[2].getName());
                    assertTrue(Modifier.isNative(m[2].getModifiers()));
                    Annotation[] a = m[2].getAnnotations();
                    assertEquals(0, a.length);
                }
            };
            cl2.add(NATIVE_CLASS_NAME, cw);
            cl2.testModifiedInstance();
        } catch (Throwable t) {
            throw dumpGeneratedClass(t, cl2);
        }
    }

    /**
     * {@link DelegateMethodAdapter} does not support overriding constructors yet,
     * so this should fail with an {@link UnsupportedOperationException}.
     *
     * Although not tested here, the message of the exception should contain the
     * constructor signature.
     */
    @Test(expected=UnsupportedOperationException.class)
    public void testConstructorsNotSupported() throws IOException {
        ClassWriter cw = new ClassWriter(0 /*flags*/);

        String internalClassName = NATIVE_CLASS_NAME.replace('.', '/');

        HashSet<String> delegateMethods = new HashSet<String>();
        delegateMethods.add("<init>");
        DelegateClassAdapter cv = new DelegateClassAdapter(
                mLog, cw, internalClassName, delegateMethods);

        ClassReader cr = new ClassReader(NATIVE_CLASS_NAME);
        cr.accept(cv, 0 /* flags */);
    }

    @Test
    public void testDelegateNative() throws Throwable {
        ClassWriter cw = new ClassWriter(0 /*flags*/);
        String internalClassName = NATIVE_CLASS_NAME.replace('.', '/');

        HashSet<String> delegateMethods = new HashSet<String>();
        delegateMethods.add(DelegateClassAdapter.ALL_NATIVES);
        DelegateClassAdapter cv = new DelegateClassAdapter(
                mLog, cw, internalClassName, delegateMethods);

        ClassReader cr = new ClassReader(NATIVE_CLASS_NAME);
        cr.accept(cv, 0 /* flags */);

        // Load the generated class in a different class loader and try it
        ClassLoader2 cl2 = null;
        try {
            cl2 = new ClassLoader2() {
                @Override
                public void testModifiedInstance() throws Exception {
                    Class<?> clazz2 = loadClass(NATIVE_CLASS_NAME);
                    Object i2 = clazz2.newInstance();
                    assertNotNull(i2);

                    // Use reflection to access inner methods
                    assertEquals(42, callAdd(i2, 20, 22));

                     Object[] objResult = new Object[] { null };
                     int result = callCallNativeInstance(i2, 10, 3.1415, objResult);
                     assertEquals((int)(10 + 3.1415), result);
                     assertSame(i2, objResult[0]);

                     // Check that the native method now has the new annotation and is not native
                     Method[] m = clazz2.getDeclaredMethods();
                     assertEquals("native_instance", m[2].getName());
                     assertFalse(Modifier.isNative(m[2].getModifiers()));
                     Annotation[] a = m[2].getAnnotations();
                     assertEquals("LayoutlibDelegate", a[0].annotationType().getSimpleName());
                }
            };
            cl2.add(NATIVE_CLASS_NAME, cw);
            cl2.testModifiedInstance();
        } catch (Throwable t) {
            throw dumpGeneratedClass(t, cl2);
        }
    }

    @Test
    public void testDelegateInner() throws Throwable {
        // We'll delegate the "get" method of both the inner and outer class.
        HashSet<String> delegateMethods = new HashSet<String>();
        delegateMethods.add("get");

        // Generate the delegate for the outer class.
        ClassWriter cwOuter = new ClassWriter(0 /*flags*/);
        String outerClassName = OUTER_CLASS_NAME.replace('.', '/');
        DelegateClassAdapter cvOuter = new DelegateClassAdapter(
                mLog, cwOuter, outerClassName, delegateMethods);
        ClassReader cr = new ClassReader(OUTER_CLASS_NAME);
        cr.accept(cvOuter, 0 /* flags */);

        // Generate the delegate for the inner class.
        ClassWriter cwInner = new ClassWriter(0 /*flags*/);
        String innerClassName = INNER_CLASS_NAME.replace('.', '/');
        DelegateClassAdapter cvInner = new DelegateClassAdapter(
                mLog, cwInner, innerClassName, delegateMethods);
        cr = new ClassReader(INNER_CLASS_NAME);
        cr.accept(cvInner, 0 /* flags */);

        // Load the generated classes in a different class loader and try them
        ClassLoader2 cl2 = null;
        try {
            cl2 = new ClassLoader2() {
                @Override
                public void testModifiedInstance() throws Exception {

                    // Check the outer class
                    Class<?> outerClazz2 = loadClass(OUTER_CLASS_NAME);
                    Object o2 = outerClazz2.newInstance();
                    assertNotNull(o2);

                    // The original Outer.get returns 1+10+20,
                    // but the delegate makes it return 4+10+20
                    assertEquals(4+10+20, callGet(o2, 10, 20));

                    // Check the inner class. Since it's not a static inner class, we need
                    // to use the hidden constructor that takes the outer class as first parameter.
                    Class<?> innerClazz2 = loadClass(INNER_CLASS_NAME);
                    Constructor<?> innerCons = innerClazz2.getConstructor(
                                                                new Class<?>[] { outerClazz2 });
                    Object i2 = innerCons.newInstance(new Object[] { o2 });
                    assertNotNull(i2);

                    // The original Inner.get returns 3+10+20,
                    // but the delegate makes it return 6+10+20
                    assertEquals(6+10+20, callGet(i2, 10, 20));
                }
            };
            cl2.add(OUTER_CLASS_NAME, cwOuter.toByteArray());
            cl2.add(INNER_CLASS_NAME, cwInner.toByteArray());
            cl2.testModifiedInstance();
        } catch (Throwable t) {
            throw dumpGeneratedClass(t, cl2);
        }
    }

    //-------

    /**
     * A class loader than can define and instantiate our modified classes.
     * <p/>
     * The trick here is that this class loader will test our <em>modified</em> version
     * of the classes, the one with the delegate calls.
     * <p/>
     * Trying to do so in the original class loader generates all sort of link issues because
     * there are 2 different definitions of the same class name. This class loader will
     * define and load the class when requested by name and provide helpers to access the
     * instance methods via reflection.
     */
    private abstract class ClassLoader2 extends ClassLoader {

        private final Map<String, byte[]> mClassDefs = new HashMap<String, byte[]>();

        public ClassLoader2() {
            super(null);
        }

        public ClassLoader2 add(String className, byte[] definition) {
            mClassDefs.put(className, definition);
            return this;
        }

        public ClassLoader2 add(String className, ClassWriter rewrittenClass) {
            mClassDefs.put(className, rewrittenClass.toByteArray());
            return this;
        }

        private Set<Entry<String, byte[]>> getByteCode() {
            return mClassDefs.entrySet();
        }

        @SuppressWarnings("unused")
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            try {
                return super.findClass(name);
            } catch (ClassNotFoundException e) {

                byte[] def = mClassDefs.get(name);
                if (def != null) {
                    // Load the modified ClassWithNative from its bytes representation.
                    return defineClass(name, def, 0, def.length);
                }

                try {
                    // Load everything else from the original definition into the new class loader.
                    ClassReader cr = new ClassReader(name);
                    ClassWriter cw = new ClassWriter(0);
                    cr.accept(cw, 0);
                    byte[] bytes = cw.toByteArray();
                    return defineClass(name, bytes, 0, bytes.length);

                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }
        }

        /**
         * Accesses {@link OuterClass#get()} or {@link InnerClass#get() }via reflection.
         */
        public int callGet(Object instance, int a, long b) throws Exception {
            Method m = instance.getClass().getMethod("get",
                    new Class<?>[] { int.class, long.class } );

            Object result = m.invoke(instance, new Object[] { a, b });
            return ((Integer) result).intValue();
        }

        /**
         * Accesses {@link ClassWithNative#add(int, int)} via reflection.
         */
        public int callAdd(Object instance, int a, int b) throws Exception {
            Method m = instance.getClass().getMethod("add",
                    new Class<?>[] { int.class, int.class });

            Object result = m.invoke(instance, new Object[] { a, b });
            return ((Integer) result).intValue();
        }

        /**
         * Accesses {@link ClassWithNative#callNativeInstance(int, double, Object[])}
         * via reflection.
         */
        public int callCallNativeInstance(Object instance, int a, double d, Object[] o)
                throws Exception {
            Method m = instance.getClass().getMethod("callNativeInstance",
                    new Class<?>[] { int.class, double.class, Object[].class });

            Object result = m.invoke(instance, new Object[] { a, d, o });
            return ((Integer) result).intValue();
        }

        public abstract void testModifiedInstance() throws Exception;
    }

    /**
     * For debugging, it's useful to dump the content of the generated classes
     * along with the exception that was generated.
     *
     * However to make it work you need to pull in the org.objectweb.asm.util.TraceClassVisitor
     * class and associated utilities which are found in the ASM source jar. Since we don't
     * want that dependency in the source code, we only put it manually for development and
     * access the TraceClassVisitor via reflection if present.
     *
     * @param t The exception thrown by {@link ClassLoader2#testModifiedInstance()}
     * @param cl2 The {@link ClassLoader2} instance with the generated bytecode.
     * @return Either original {@code t} or a new wrapper {@link Throwable}
     */
    private Throwable dumpGeneratedClass(Throwable t, ClassLoader2 cl2) {
        try {
            // For debugging, dump the bytecode of the class in case of unexpected error
            // if we can find the TraceClassVisitor class.
            Class<?> tcvClass = Class.forName("org.objectweb.asm.util.TraceClassVisitor");

            StringBuilder sb = new StringBuilder();
            sb.append('\n').append(t.getClass().getCanonicalName());
            if (t.getMessage() != null) {
                sb.append(": ").append(t.getMessage());
              }

            for (Entry<String, byte[]> entry : cl2.getByteCode()) {
                String className = entry.getKey();
                byte[] bytes = entry.getValue();

                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                // next 2 lines do: TraceClassVisitor tcv = new TraceClassVisitor(pw);
                Constructor<?> cons = tcvClass.getConstructor(new Class<?>[] { pw.getClass() });
                Object tcv = cons.newInstance(new Object[] { pw });
                ClassReader cr2 = new ClassReader(bytes);
                cr2.accept((ClassVisitor) tcv, 0 /* flags */);

                sb.append("\nBytecode dump: <").append(className).append(">:\n")
                  .append(sw.toString());
            }

            // Re-throw exception with new message
            RuntimeException ex = new RuntimeException(sb.toString(), t);
            return ex;
        } catch (Throwable ignore) {
            // In case of problem, just throw the original exception as-is.
            return t;
        }
    }

}
