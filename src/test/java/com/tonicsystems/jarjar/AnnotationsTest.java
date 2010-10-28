/**
 * Copyright 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tonicsystems.jarjar;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import com.tonicsystems.jarjar.annotations.EnumUsedByAnnotation;
import com.tonicsystems.jarjar.annotations.ExampleAnnotated;
import com.tonicsystems.jarjar.annotations.ExampleAnnotation;
import com.tonicsystems.jarjar.util.ClassTransformer;
import com.tonicsystems.jarjar.util.RemappingClassTransformer;

// TODO: This test makes a ".class" resource classloading assumption, is this reasonable for tests?
public class AnnotationsTest extends TestCase {

    public void testAnnotatedClass() throws Exception {
        final Rule rule = new Rule();
        rule.setPattern("com.tonicsystems.jarjar.annotations.**");
        rule.setResult("repackaged.@0");
        
        final ClassTransformer transformer = new RemappingClassTransformer(new PackageRemapper(Arrays.asList(rule), false));
        final Map<String, byte[]> results = rewrite(transformer, ExampleAnnotated.class, ExampleAnnotation.class, EnumUsedByAnnotation.class);

        final Class<?> repackaged = Class.forName("repackaged." + ExampleAnnotated.class.getName(), true, new ClassLoader() {
            @Override
            protected Class<?> findClass(final String name) throws ClassNotFoundException {
                byte[] bytes = results.get(name);
                if (bytes != null) {
                    return defineClass(null, bytes, 0, bytes.length);
                }
                return super.findClass(name);
            }
        });

        assertSingleRepackagedAnnotation(repackaged);
        Method method = repackaged.getMethod("foo", String.class);
        assertSingleRepackagedAnnotation(method);
        Annotation[][] parameterAnnotations = method.getParameterAnnotations();
        assertEquals(1, parameterAnnotations.length);
        assertSingleRepackagedAnnotation(parameterAnnotations[0]);
        assertSingleRepackagedAnnotation(repackaged.getField("foo"));
    }

    private Map<String, byte[]> rewrite(ClassTransformer transformer, Class<?>... classes) throws IOException {
        Map<String, byte[]> results = new HashMap<String, byte[]>();
        for (Class<?> clazz : classes) {
            results.put("repackaged." + clazz.getName(), rewrite("annotations/" + clazz.getSimpleName() + ".class", transformer));
        }
        return results;
    }

    private void assertSingleRepackagedAnnotation(final AnnotatedElement element) {
        Annotation[] annotations = element.getAnnotations();
        assertSingleRepackagedAnnotation(annotations);
    }

    private void assertSingleRepackagedAnnotation(final Annotation[] annotations) {
        assertEquals(1, annotations.length);
        assertEquals("repackaged." + ExampleAnnotation.class.getName(), annotations[0].annotationType().getName());
    }

    private byte[] rewrite(final String resource, final ClassTransformer t) throws IOException {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        t.setTarget(writer);
        ClassReader reader = new ClassReader(getClass().getResourceAsStream(resource));
        reader.accept(t, 0);
        return writer.toByteArray();
    }

    public AnnotationsTest(final String name) {
        super(name);
    }

    public static Test suite() {
        return new TestSuite(AnnotationsTest.class);
    }

}
