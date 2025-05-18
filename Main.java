package com.example;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.index.*;
import org.apache.lucene.document.*;
import org.apache.lucene.search.*;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.queryparser.classic.QueryParser;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main {
    // Porter stemmer + lower case custom analyzer
    static class CustomAnalyzer extends Analyzer {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new StandardTokenizer();
            TokenStream filter = new LowerCaseFilter(tokenizer);
            filter = new PorterStemFilter(filter);
            return new TokenStreamComponents(tokenizer, filter);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: java Main <corpus.jsonl> <queries.jsonl> <relevance.tsv>");
            System.exit(1);
        }

        String corpusPath = args[0];
        String queriesPath = args[1];
        String relevancePath = args[2];

        Analyzer analyzer = new CustomAnalyzer();
        Directory index = new RAMDirectory();

        // 1. İndeksleme
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        IndexWriter writer = new IndexWriter(index, config);
        List<String> lines = Files.readAllLines(Paths.get(corpusPath));
        for (String line : lines) {
            // Basit jsonl ayrıştırma
            String id = line.split("\"id\":")[1].split(",")[0].replace("\"", "").trim();
            String text = line.split("\"text\":")[1].replace("\"", "").trim();
            Document doc = new Document();
            doc.add(new StringField("id", id, Field.Store.YES));
            doc.add(new TextField("text", text, Field.Store.YES));
            writer.addDocument(doc);
        }
        writer.close();

        // 2. Arama
        IndexReader reader = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new BM25Similarity());  // ya da LMDirichletSimilarity()

        List<String> queryLines = Files.readAllLines(Paths.get(queriesPath));
        for (String queryLine : queryLines) {
            String topic = queryLine.split("\"_id\":")[1].split(",")[0].replace("\"", "").trim();
            String queryText = queryLine.split("\"text\":")[1].replace("\"", "").trim();
            QueryParser parser = new QueryParser("text", analyzer);
            Query query = parser.parse(queryText);
            TopDocs topDocs = searcher.search(query, 100);
            int rank = 1;
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.doc(sd.doc);
                System.out.println(topic + " Q0 " + doc.get("id") + " " + rank + " " + sd.score + " myrun");
                rank++;
            }
        }

        // 3. nDCG hesaplaması ayrı bir modülde yapılmalı (isteğe bağlı eklenebilir)
    }
}
