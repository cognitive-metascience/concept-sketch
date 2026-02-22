package pl.marcinmilkowski.word_sketch.query;

import org.junit.jupiter.api.Test;
import nl.inl.blacklab.search.results.Kwics;
import java.lang.reflect.Method;

public class FindKwicTest {
    @Test
    public void test() throws Exception {
        for (Method m : Kwics.class.getMethods()) {
            if (m.getName().equals("get")) {
                System.out.println("Kwics.get() returns: " + m.getReturnType().getName());
            }
        }
    }
}
