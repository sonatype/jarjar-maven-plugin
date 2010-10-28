package com.tonicsystems.jarjar.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface ExampleAnnotation {
  EnumUsedByAnnotation value() default EnumUsedByAnnotation.SOME;
}
