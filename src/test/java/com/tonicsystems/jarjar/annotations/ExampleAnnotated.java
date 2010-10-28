package com.tonicsystems.jarjar.annotations;

@ExampleAnnotation
public class ExampleAnnotated {

    @ExampleAnnotation
    public int foo;
    
    @ExampleAnnotation
    public void foo(@ExampleAnnotation final String argument) {
        ExampleAnnotation annotation = null;
    }

}
