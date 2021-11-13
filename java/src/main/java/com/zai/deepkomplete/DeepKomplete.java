package com.zai.deepkomplete;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class DeepKomplete {

    // CSV file paths
    private final static String brandsPath = "../data/brands.csv";
    private final static String categoriesPath = "../data/categories.csv";
    private final static String linesPath = "../data/lines.csv";
    private final static String bclIndexingPath = "../data/bcl_indexing.csv";
    private final static String similarityMatrixPath = "../data/similarity_matrix.csv";

    // Suggestion model parameters
    private final static float discountRate = 0.9f;
    private final static int numRecents = 10;
    private final static int numSuggestions = 30;

    // Mappings
    private Map<String, String> synonymMap;
    private Map<String, String> brandLineSynonymMap;
    private Map<String, String> categorySynonymMap;
    private Map<Integer, String> idx2bcl;
    private Map<String, Integer> bcl2idx;
    private Map<String, String> bcl2bcl;

    // Similarity matrix
    private float[][] similarityMatrix;

    public DeepKomplete() throws IOException {

        // Initialize maps
        synonymMap = new HashMap<>(4000, 0.7f);
        brandLineSynonymMap = new HashMap<>(2000, 0.7f);
        categorySynonymMap = new HashMap<>(2000, 0.7f);
        idx2bcl = new HashMap<>(5000, 0.7f);
        bcl2idx = new HashMap<>(5000, 0.7f);
        bcl2bcl = new HashMap<>(20000, 0.7f);

        // Read CSVs
        try (
            FileReader brandsFr = new FileReader(brandsPath);    
            BufferedReader brandsBr = new BufferedReader(brandsFr);
        ) {
            String line;
            brandsBr.readLine(); // skip header
            while ((line = brandsBr.readLine()) != null) {
                String[] values = line.split(",", -1);
                String name = values[1].trim();
                String engName = values[2].trim().toUpperCase();
                String[] synonyms = values[3].trim().toUpperCase().split("\\|", -1);

                if (name.length() == 0) continue;
                synonymMap.put(name, name);
                brandLineSynonymMap.put(name, name);

                if (engName.length() > 0) {
                    synonymMap.put(engName, name);
                    brandLineSynonymMap.put(engName, name);
                }

                for (String synonym : synonyms) {
                    if (synonym.length() > 0) {
                        synonymMap.put(synonym.trim(), name);
                        brandLineSynonymMap.put(synonym.trim(), name);
                    }
                }
            }
        }

        try (
            FileReader categoriesFr = new FileReader(categoriesPath);    
            BufferedReader categoriesBr = new BufferedReader(categoriesFr);
        ) {
            String line;
            categoriesBr.readLine(); // skip header
            while ((line = categoriesBr.readLine()) != null) {
                String[] values = line.split(",", -1);
                String name = values[1].trim();
                String[] synonyms = values[2].trim().toUpperCase().split("\\|", -1);

                if (name.length() == 0) continue;
                synonymMap.put(name, name);
                categorySynonymMap.put(name, name);

                for (String synonym : synonyms) {
                    if (synonym.length() > 0) {
                        synonymMap.put(synonym.trim(), name);
                        categorySynonymMap.put(synonym.trim(), name);
                    }
                }
            }
        }

        try (
            FileReader linesFr = new FileReader(linesPath);    
            BufferedReader linesBr = new BufferedReader(linesFr);
        ) {
            String line;
            linesBr.readLine(); // skip header
            while ((line = linesBr.readLine()) != null) {
                String[] values = line.split(",", -1);
                String name = values[1].trim();
                String[] synonyms = values[2].trim().toUpperCase().split("\\|", -1);

                if (name.length() == 0) continue;
                synonymMap.put(name, name);
                brandLineSynonymMap.put(name, name);

                for (String synonym : synonyms) {
                    if (synonym.length() > 0) {
                        synonymMap.put(synonym.trim(), name);
                        brandLineSynonymMap.put(synonym.trim(), name);
                    }
                }
            }
        }

        try (
            FileReader bclFr = new FileReader(bclIndexingPath);    
            BufferedReader bclBr = new BufferedReader(bclFr);
        ) {
            String bcl;
            for (int idx = 0; (bcl = bclBr.readLine()) != null; idx++) {

                if (bcl.length() == 0) continue;

                idx2bcl.put(idx, bcl);
                bcl2idx.put(bcl, idx);
                bcl2bcl.put(bcl, bcl);

                String[] keywords = bcl.split(" ", -1);
                int[] indices = new int[keywords.length];
                for (int idx1 = 0; idx1 < keywords.length; ) {
                    if (indices[idx1] < idx1) {
                        int idx2 = idx1 % 2 == 0 ? 0 : indices[idx1];
                        
                        String temp = keywords[idx1];
                        keywords[idx1] = keywords[idx2];
                        keywords[idx2] = temp;
                        
                        bcl2bcl.put(String.join(" ", keywords), bcl);
                        
                        indices[idx1]++;
                        idx1 = 0;
                    } else {
                        indices[idx1] = 0;
                        idx1++;
                    }
                }
            }
        }

        // Read similarity matrix
        try (
            FileReader smFr = new FileReader(similarityMatrixPath);    
            BufferedReader smBr = new BufferedReader(smFr);
        ) {
            String line = smBr.readLine();
            String[] values = line.split(",", -1);
            int length = values.length;

            similarityMatrix = new float[length + 1][length];
            for (int i = 0; i < length; i++) 
                similarityMatrix[0][i] = Float.parseFloat(values[i]);

            for (int i = 1; (line = smBr.readLine()) != null; i++) {
                if (line.length() == 0) continue;
                values = line.split(",", -1);
                for (int j = 0; j < length; j++)
                    similarityMatrix[i][j] = Float.parseFloat(values[j]);
            }
        }
    }

    private List<Set<String>> stochasticQueryAnalysis(String query) {

        for (int length = query.length(); length > 0; length--) {
            
            String subquery = query.substring(0, length);
            Set<String> foundKeywords = new HashSet<String>(10);

            for (String brandOrLine : brandLineSynonymMap.keySet()) {
                if (brandOrLine.startsWith(subquery))
                    foundKeywords.add(brandLineSynonymMap.get(brandOrLine));
            }

            for (String category : categorySynonymMap.keySet()) {
                if (category.contains(subquery))
                    foundKeywords.add(categorySynonymMap.get(category));
            }

            if (foundKeywords.size() == 0) continue;

            ArrayList<Set<String>> candidates = new ArrayList<Set<String>>();
            candidates.add(foundKeywords);
            if (length < query.length())
                candidates.addAll(stochasticQueryAnalysis(query.substring(length)));
            return candidates;
        }

        return Collections.emptyList();
    }

    private Set<String> deterministicQueryAnalysis(String query) {

        for (int length = query.length(); length > 0; length--) {
            for (int startIndex = query.length() - length; startIndex >= 0; startIndex--) {

                String subquery = query.substring(startIndex, startIndex + length);

                if (synonymMap.containsKey(subquery)) {
                    Set<String> foundKeywords = new HashSet<String>(10);
                    foundKeywords.add(synonymMap.get(subquery));
                    foundKeywords.addAll(
                        deterministicQueryAnalysis(query.substring(0, startIndex)));
                    foundKeywords.addAll(
                        deterministicQueryAnalysis(query.substring(startIndex + length)));
                    return foundKeywords;
                }
            }
        }
        
        return Collections.emptySet();
    }

    private String queryTransform(String query) {

        if (bcl2bcl.containsKey(query)) return bcl2bcl.get(query);
        query = query.toUpperCase();
        if (bcl2bcl.containsKey(query)) return bcl2bcl.get(query);

        String[] foundKeywords = deterministicQueryAnalysis(query).toArray(new String[0]);
        if (foundKeywords.length == 0) return null;

        int n = foundKeywords.length;
        int[] combination = new int[n];
        for (int r = n; r > 0; r--) {

            String[] candidate = new String[r];
            for (int i = 0; i < r; i++) combination[i] = i;

            while (combination[r - 1] < n) {
                for (int index = 0; index < r; index++)
                    candidate[index] = foundKeywords[combination[index]];
                String name = String.join(" ", candidate);
                if (bcl2bcl.containsKey(name)) return bcl2bcl.get(name);
                
                int t = r - 1;
                while (t != 0 && combination[t] == n - r + t) t--;
                combination[t]++;
                for (int i = t + 1; i < r; i++) combination[i] = combination[i - 1] + 1;
            }
        }
        
        return null;
    }

    private float[] getSimilarityVector(List<String> history) {
        float[] similarityVector = new float[similarityMatrix[0].length];
        float discount = 1.0f;
        int historyLength = numRecents > history.size() ? history.size() : numRecents;

        for (int i = 0; i < historyLength; i++) {
            String query = queryTransform(history.get(i));
            if (query == null) continue;
            int queryEncoded = bcl2idx.get(query);
            for (int j = 0; j < similarityVector.length; j++)
                similarityVector[j] += discount * similarityMatrix[queryEncoded][j];
            discount *= discountRate;
        }

        if (discount >= 1.0) return similarityMatrix[similarityMatrix.length - 1];
        return similarityVector;
    }

    public List<String> suggest(String query, List<String> history) {

        query = query.toUpperCase().replaceAll("\\s","");
        List<Set<String>> candidates = stochasticQueryAnalysis(query);
        float[] similarityVector = getSimilarityVector(history);

        List<String> queryRanking = IntStream.range(0, similarityVector.length)
            .boxed()
            .parallel()
            .sorted((i1, i2) -> -Float.compare(similarityVector[i1], similarityVector[i2]))
            .map(i -> idx2bcl.get(i))
            .filter(q -> {
                boolean doesMatch = true;
                for (Set<String> candidateSet : candidates) {
                    boolean doesSubMatch = false;
                    for (String candidate : candidateSet)
                        doesSubMatch = q.contains(candidate) || doesSubMatch;
                    doesMatch = doesMatch && doesSubMatch;
                }
                return doesMatch;
            })
            .limit(numSuggestions)
            .collect(Collectors.toList());
        
        return queryRanking;
    }
}
