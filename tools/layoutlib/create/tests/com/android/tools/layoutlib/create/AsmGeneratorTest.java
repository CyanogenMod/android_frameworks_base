/*
 * Copyright (C) 2008 The Android Open Source Project
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


import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Unit tests for some methods of {@link AsmGenerator}.
 */
public class AsmGeneratorTest {

    private MockLog mLog;
    private ArrayList<String> mOsJarPath;
    private String mOsDestJar;
    private File mTempFile;

    // ASM internal name for the the class in java package that should be refactored.
    private static final String JAVA_CLASS_NAME = "java/lang/JavaClass";

    @Before
    public void setUp() throws Exception {
        mLog = new MockLog();
        URL url = this.getClass().getClassLoader().getResource("data/mock_android.jar");

        mOsJarPath = new ArrayList<String>();
        mOsJarPath.add(url.getFile());

        mTempFile = File.createTempFile("mock", ".jar");
        mOsDestJar = mTempFile.getAbsolutePath();
        mTempFile.deleteOnExit();
    }

    @After
    public void tearDown() throws Exception {
        if (mTempFile != null) {
            mTempFile.delete();
            mTempFile = null;
        }
    }

    @Test
    public void testClassRenaming() throws IOException, LogAbortException {

        ICreateInfo ci = new ICreateInfo() {
            @Override
            public Class<?>[] getInjectedClasses() {
                // classes to inject in the final JAR
                return new Class<?>[0];
            }

            @Override
            public String[] getDelegateMethods() {
                return new String[0];
            }

            @Override
            public String[] getDelegateClassNatives() {
                return new String[0];
            }

            @Override
            public String[] getOverriddenMethods() {
                // methods to force override
                return new String[0];
            }

            @Override
            public String[] getRenamedClasses() {
                // classes to rename (so that we can replace them)
                return new String[] {
                        "mock_android.view.View", "mock_android.view._Original_View",
                        "not.an.actual.ClassName", "anoter.fake.NewClassName",
                };
            }

            @Override
            public String[] getJavaPkgClasses() {
              return new String[0];
            }

            @Override
            public String[] getDeleteReturns() {
                 // methods deleted from their return type.
                return new String[0];
            }
        };

        AsmGenerator agen = new AsmGenerator(mLog, mOsDestJar, ci);

        AsmAnalyzer aa = new AsmAnalyzer(mLog, mOsJarPath, agen,
                null,                 // derived from
                new String[] {        // include classes
                    "**"
                },
                new HashSet<String>(0) /* excluded classes */,
                new String[]{} /* include files */);
        aa.analyze();
        agen.generate();

        Set<String> notRenamed = agen.getClassesNotRenamed();
        assertArrayEquals(new String[] { "not/an/actual/ClassName" }, notRenamed.toArray());

    }

    @Test
    public void testClassRefactoring() throws IOException, LogAbortException {
        ICreateInfo ci = new ICreateInfo() {
            @Override
            public Class<?>[] getInjectedClasses() {
                // classes to inject in the final JAR
                return new Class<?>[] {
                        com.android.tools.layoutlib.create.dataclass.JavaClass.class
                };
            }

            @Override
            public String[] getDelegateMethods() {
                return new String[0];
            }

            @Override
            public String[] getDelegateClassNatives() {
                return new String[0];
            }

            @Override
            public String[] getOverriddenMethods() {
                // methods to force override
                return new String[0];
            }

            @Override
            public String[] getRenamedClasses() {
                // classes to rename (so that we can replace them)
                return new String[0];
            }

            @Override
            public String[] getJavaPkgClasses() {
             // classes to refactor (so that we can replace them)
                return new String[] {
                        "java.lang.JavaClass", "com.android.tools.layoutlib.create.dataclass.JavaClass",
                };
            }

            @Override
            public String[] getDeleteReturns() {
                 // methods deleted from their return type.
                return new String[0];
            }
        };

        AsmGenerator agen = new AsmGenerator(mLog, mOsDestJar, ci);

        AsmAnalyzer aa = new AsmAnalyzer(mLog, mOsJarPath, agen,
                null,                 // derived from
                new String[] {        // include classes
                    "**"
                },
                new HashSet<String>(1),
                new String[] {        /* include files */
                    "mock_android/data/data*"
                });
        aa.analyze();
        agen.generate();
        Map<String, ClassReader> output = new TreeMap<String, ClassReader>();
        Map<String, InputStream> filesFound = new TreeMap<String, InputStream>();
        parseZip(mOsDestJar, output, filesFound);
        boolean injectedClassFound = false;
        for (ClassReader cr: output.values()) {
            TestClassVisitor cv = new TestClassVisitor();
            cr.accept(cv, 0);
            injectedClassFound |= cv.mInjectedClassFound;
        }
        assertTrue(injectedClassFound);
        assertArrayEquals(new String[] {"mock_android/data/dataFile"},
                filesFound.keySet().toArray());
    }

    private void parseZip(String jarPath,
            Map<String, ClassReader> classes,
            Map<String, InputStream> filesFound) throws IOException {

            ZipFile zip = new ZipFile(jarPath);
            Enumeration<? extends ZipEntry> entries = zip.entries();
            ZipEntry entry;
            while (entries.hasMoreElements()) {
                entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    ClassReader cr = new ClassReader(zip.getInputStream(entry));
                    String className = classReaderToClassName(cr);
                    classes.put(className, cr);
                } else {
                    filesFound.put(entry.getName(), zip.getInputStream(entry));
                }
            }

    }

    private String classReaderToClassName(ClassReader classReader) {
        if (classReader == null) {
            return null;
        } else {
            return classReader.getClassName().replace('/', '.');
        }
    }

    private class TestClassVisitor extends ClassVisitor {

        boolean mInjectedClassFound = false;

        TestClassVisitor() {
            super(Opcodes.ASM4);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            assertTrue(!getBase(name).equals(JAVA_CLASS_NAME));
            if (name.equals("com/android/tools/layoutlib/create/dataclass/JavaClass")) {
                mInjectedClassFound = true;
            }
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc,
                String signature, Object value) {
            assertTrue(testType(Type.getType(desc)));
            return super.visitField(access, name, desc, signature, value);
        }

        @SuppressWarnings("hiding")
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            return new MethodVisitor(Opcodes.ASM4, mv) {

                @Override
                public void visitFieldInsn(int opcode, String owner, String name,
                        String desc) {
                    assertTrue(!getBase(owner).equals(JAVA_CLASS_NAME));
                    assertTrue(testType(Type.getType(desc)));
                    super.visitFieldInsn(opcode, owner, name, desc);
                }

                @Override
                public void visitLdcInsn(Object cst) {
                    if (cst instanceof Type) {
                        assertTrue(testType((Type)cst));
                    }
                    super.visitLdcInsn(cst);
                }

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    assertTrue(!getBase(type).equals(JAVA_CLASS_NAME));
                    super.visitTypeInsn(opcode, type);
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name,
                        String desc) {
                    assertTrue(!getBase(owner).equals(JAVA_CLASS_NAME));
                    assertTrue(testType(Type.getType(desc)));
                    super.visitMethodInsn(opcode, owner, name, desc);
                }

            };
        }

        private boolean testType(Type type) {
            int sort = type.getSort();
            if (sort == Type.OBJECT) {
                assertTrue(!getBase(type.getInternalName()).equals(JAVA_CLASS_NAME));
            } else if (sort == Type.ARRAY) {
                assertTrue(!getBase(type.getElementType().getInternalName())
                        .equals(JAVA_CLASS_NAME));
            } else if (sort == Type.METHOD) {
                boolean r = true;
                for (Type t : type.getArgumentTypes()) {
                    r &= testType(t);
                }
                return r & testType(type.getReturnType());
            }
            return true;
        }

        private String getBase(String className) {
            if (className == null) {
                return null;
            }
            int pos = className.indexOf('$');
            if (pos > 0) {
                return className.substring(0, pos);
            }
            return className;
        }
    }
}
