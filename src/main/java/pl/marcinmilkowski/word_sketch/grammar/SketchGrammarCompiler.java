package pl.marcinmilkowski.word_sketch.grammar;

import org.apache.lucene.queries.spans.SpanQuery;
import pl.marcinmilkowski.word_sketch.query.CQLToLuceneCompiler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiler that converts a SketchGrammar to executable Lucene queries.
 */
public class SketchGrammarCompiler {

    private final CQLToLuceneCompiler cqlCompiler;
    private final Map<String, List<SpanQuery>> compiledRelations;

    public SketchGrammarCompiler() {
        this.cqlCompiler = new CQLToLuceneCompiler();
        this.compiledRelations = new HashMap<>();
    }

    public CompiledSketch compile(SketchGrammarParser.SketchGrammar grammar) {
        List<CompiledRelation> relations = new ArrayList<>();

        for (SketchGrammarParser.SketchRelation rel : grammar.getRelations()) {
            CompiledRelation compiled = compileRelation(rel);
            relations.add(compiled);
            compiledRelations.put(rel.getName(), compiled.getQueries());
        }

        return new CompiledSketch(relations, grammar.getDefaultAttr());
    }

    private CompiledRelation compileRelation(SketchGrammarParser.SketchRelation rel) {
        List<SpanQuery> queries = new ArrayList<>();
        CQLParser.ParsedCQL pattern = rel.getPattern();

        switch (rel.getType()) {
            case DUAL:
                queries.addAll(compilePatternWithExpansion(pattern, true));
                break;
            case TRINARY:
                queries.addAll(compileBasicPattern(pattern));
                break;
            case UNARY:
                queries.addAll(compileBasicPattern(pattern));
                break;
            case UNIMAP:
                queries.addAll(compilePatternWithExpansion(pattern, false));
                break;
            default:
                queries.addAll(compileBasicPattern(pattern));
        }

        return new CompiledRelation(rel.getName(), queries, rel.getType());
    }

    private List<SpanQuery> compileBasicPattern(CQLParser.ParsedCQL pattern) {
        List<SpanQuery> queries = new ArrayList<>();

        if (!pattern.getElements().isEmpty()) {
            CQLPattern cqlPattern = new CQLPattern();
            for (CQLPattern.PatternElement elem : pattern.getElements()) {
                cqlPattern.addElement(elem);
            }
            queries.add(cqlCompiler.compile(cqlPattern));

            for (String alt : pattern.getAlternatives()) {
                try {
                    CQLPattern altPattern = new CQLParser().parse(alt);
                    queries.add(cqlCompiler.compile(altPattern));
                } catch (Exception e) {
                    // Skip invalid alternatives
                }
            }
        }

        return queries;
    }

    private List<SpanQuery> compilePatternWithExpansion(CQLParser.ParsedCQL pattern, boolean includeReverse) {
        List<SpanQuery> queries = compileBasicPattern(pattern);

        if (includeReverse && !pattern.getElements().isEmpty()) {
            CQLPattern reversePattern = createReversePattern(pattern);
            queries.add(cqlCompiler.compile(reversePattern));
        }

        return queries;
    }

    private CQLPattern createReversePattern(CQLParser.ParsedCQL original) {
        CQLPattern reverse = new CQLPattern();

        for (CQLPattern.PatternElement elem : original.getElements()) {
            int pos = elem.getPosition();
            if (pos == 1) {
                reverse.addElement(new CQLPattern.PatternElement(2, elem.getTarget(),
                    elem.getConstraint(), elem.getMinRepetition(), elem.getMaxRepetition(),
                    elem.getMinDistance(), elem.getMaxDistance(), elem.getLabel()));
            } else if (pos == 2) {
                reverse.addElement(new CQLPattern.PatternElement(1, elem.getTarget(),
                    elem.getConstraint(), elem.getMinRepetition(), elem.getMaxRepetition(),
                    elem.getMinDistance(), elem.getMaxDistance(), elem.getLabel()));
            } else {
                reverse.addElement(elem);
            }
        }

        return reverse;
    }

    public List<SpanQuery> getRelationQueries(String relationName) {
        return compiledRelations.getOrDefault(relationName, new ArrayList<>());
    }

    public static class CompiledSketch {
        private final List<CompiledRelation> relations;
        private final String defaultAttr;

        public CompiledSketch(List<CompiledRelation> relations, String defaultAttr) {
            this.relations = relations;
            this.defaultAttr = defaultAttr;
        }

        public List<CompiledRelation> getRelations() { return relations; }
        public String getDefaultAttr() { return defaultAttr; }
    }

    public static class CompiledRelation {
        private final String name;
        private final List<SpanQuery> queries;
        private final SketchGrammarParser.RelationType type;

        public CompiledRelation(String name, List<SpanQuery> queries, SketchGrammarParser.RelationType type) {
            this.name = name;
            this.queries = queries;
            this.type = type;
        }

        public String getName() { return name; }
        public List<SpanQuery> getQueries() { return queries; }
        public SketchGrammarParser.RelationType getType() { return type; }
        public boolean isEmpty() { return queries.isEmpty(); }
    }
}
