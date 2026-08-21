package com.example.autocomplete.service;

import com.example.autocomplete.model.SearchTerm;
import com.example.autocomplete.trie.TrieService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.List;

@Service
public class DataLoaderService {

    private final TrieService trieService;
    private final ObjectMapper objectMapper;

    @Value("${autocomplete.dataset.path:data/search_terms.json}")
    private String datasetPath;

    public DataLoaderService(TrieService trieService, ObjectMapper objectMapper) {
        this.trieService = trieService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadData() {
        try {
            ClassPathResource resource = new ClassPathResource(datasetPath);
            InputStream inputStream = resource.getInputStream();
            List<SearchTerm> terms = objectMapper.readValue(inputStream, new TypeReference<List<SearchTerm>>() {});

            for (SearchTerm term : terms) {
                trieService.insert(term.getTerm(), term.getFrequency());
            }
            System.out.println("Loaded " + terms.size() + " terms into Trie");
        } catch (Exception e) {
            System.err.println("Failed to load dataset: " + e.getMessage());
        }
    }
}
