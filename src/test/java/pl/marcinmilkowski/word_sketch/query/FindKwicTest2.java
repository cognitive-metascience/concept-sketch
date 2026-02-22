package pl.marcinmilkowski.word_sketch.query;

import org.junit.jupiter.api.Test;
import nl.inl.blacklab.search.Kwic;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class FindKwicTest2 {
    @Test
    public void test() throws Exception {
        System.out.println("Methods of Kwic:");
        for (Method m : Kwic.class.getMethods()) {
            if (m.getName().equals("match")) {
                System.out.print("match(");
                for (Parameter p : m.getParameters()) {
                    System.out.print(p.getType().getName() + " ");
                }
                System.out.println(") returns " + m.getReturnType().getName());
            } else if (m.getName().equals("tokens")) {
                System.out.println("tokens() exists returning: " + m.getReturnType().getName());
            }
        }
    }
}
