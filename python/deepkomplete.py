import itertools
import numpy as np
import pandas as pd

class DeepKomplete:

    def __init__(
        self,
        brands='../data/brands.csv',
        categories='../data/categories.csv',
        lines='../data/lines.csv',
        bcl_indexing='../data/bcl_indexing.csv',
        similarity_matrix='../data/similarity_matrix.csv',
        discount_rate=0.9,
        num_recents=10,
        num_suggestions=30
    ):
        
        # Load data
        brands_df = pd.read_csv(brands)
        categories_df = pd.read_csv(categories)
        lines_df = pd.read_csv(lines)
        bcl_indexing_df = pd.read_csv(bcl_indexing, header=None).values[:, 0]
        
        # Generate BCL mappings
        self.bcl2idx = {bcl: idx for idx, bcl in enumerate(bcl_indexing_df)}
        self.idx2bcl = {idx: bcl for idx, bcl in enumerate(bcl_indexing_df)}
        self.bcl2bcl = dict()
        for key in list(self.bcl2idx.keys()):
            keywords = key.split(' ')
            for permutation in itertools.permutations(keywords):
                self.bcl2bcl[' '.join(permutation)] = key
                
        # Generate synonym mapping
        self.synonym_dict = dict()
        self.brands_lines_synonym_dict = dict()
        self.categories_synonym_dict = dict()
        
        for name, name_eng, synonyms in brands_df[[
            'name', 'name_eng', 'synonyms'
        ]].fillna('').values:
            name = name.strip()
            name_eng = name_eng.strip()
            if len(name) == 0:
                continue
            self.synonym_dict[name] = name
            self.brands_lines_synonym_dict[name] = name
            if len(name_eng) > 0:
                self.synonym_dict[name_eng] = name
                self.brands_lines_synonym_dict[name_eng] = name
            for synonym in synonyms.split('|'):
                synonym = synonym.strip()
                if len(synonym) > 0:
                    self.synonym_dict[synonym] = name
                    self.brands_lines_synonym_dict[synonym] = name

        for name, synonyms in categories_df[[
            'name', 'synonyms'
        ]].fillna('').values:
            name = name.strip()
            if len(name) == 0:
                continue
            self.synonym_dict[name] = name
            self.categories_synonym_dict[name] = name
            for synonym in synonyms.split('|'):
                synonym = synonym.strip()
                if len(synonym) > 0:
                    self.synonym_dict[synonym] = name
                    self.categories_synonym_dict[synonym] = name

        for name, synonyms in lines_df[[
            'name', 'synonyms'
        ]].fillna('').values:
            name = name.strip()
            if len(name) == 0:
                continue
            self.synonym_dict[name] = name
            self.brands_lines_synonym_dict[name] = name
            for synonym in synonyms.split('|'):
                synonym = synonym.strip()
                if len(synonym) > 0:
                    self.synonym_dict[synonym] = name
                    self.brands_lines_synonym_dict[synonym] = name
        
        # Load similarity matrix
        self.similarity_matrix = pd.read_csv(
            similarity_matrix, header=None, dtype=np.float32
        ).values
        
        # Save parameters
        self.discount_rate = discount_rate
        self.num_recents = num_recents
        self.num_suggestions = num_suggestions


    def stochastic_query_analysis(self, query):
        '''Generate brand/category/line candidates from the given query'''

        for length in range(len(query), 0, -1):
            
            subquery = query[:length]
            found_keywords = set()
            
            for brand in self.brands_lines_synonym_dict:
                if brand.startswith(subquery):
                    found_keywords.add(
                        self.brands_lines_synonym_dict[brand])
            
            for category in self.categories_synonym_dict:
                if subquery in category:
                    found_keywords.add(
                        self.categories_synonym_dict[category])
            
            if len(found_keywords) > 0:
                candidates_sequential = [found_keywords]
                if length != len(query):
                    remaining_query = query[length:]
                    _candidates_sequential = self.stochastic_query_analysis(remaining_query)
                    candidates_sequential.extend(_candidates_sequential)
                return candidates_sequential
            
        return []
    

    def deterministic_query_analysis(self, query):
        '''Identify all brand/category/line from the given query'''

        found_keywords = set()
        for length in range(len(query), 0, -1):
            for start_index in range(len(query) - length, -1, -1):
                subquery = query[start_index:start_index+length]
                if subquery in self.synonym_dict:
                    found_keywords.add(self.synonym_dict[subquery])
                    found_keywords = found_keywords.union(
                        self.deterministic_query_analysis(query[:start_index]))
                    found_keywords = found_keywords.union(
                        self.deterministic_query_analysis(query[start_index+length:]))
                    return found_keywords
                
        return found_keywords

    
    def query_transform(self, query):
        '''Transform a past search query into its well-defined form'''

        if query in self.bcl2bcl:
            return self.bcl2bcl[query]
        
        query = query.upper()
    
        if query in self.bcl2bcl:
            return self.bcl2bcl[query]

        found_keywords = self.deterministic_query_analysis(query)

        if len(found_keywords) == 0:
            return None

        for r in range(len(found_keywords), 0, -1):
            for combination in itertools.combinations(found_keywords, r):
                name = ' '.join(combination)
                if name in self.bcl2bcl:
                    return self.bcl2bcl[name]

        return None


    def get_similarity_vector(self, history):
        '''Calculate personalized similarity vector
        
        Args:
            history: a list of past queries. The most recent query comes first.
        '''

        similarity_vector = np.zeros((self.similarity_matrix.shape[1],))
        discount = 1.0

        for query in history[:min(len(history), self.num_recents)]:
            query_mapped = self.query_transform(query)
            if query_mapped is None:
                continue
            query_encoded = self.bcl2idx[query_mapped]
            similarity_vector += discount * self.similarity_matrix[query_encoded]
            discount *= self.discount_rate

        if discount >= 1.0: # No valid history
            similarity_vector = self.similarity_matrix[-1]

        return similarity_vector

    
    def suggest(self, query, history):
        '''Suggest a set of queries given the current query and user history'''

        query = query.upper().replace(' ', '')
        keyword_candidates = self.stochastic_query_analysis(query)
        
        similarity_vector = self.get_similarity_vector(history)
        query_ranking_indices = np.argsort(similarity_vector, axis=0)[::-1]
        query_ranking = pd.Series(query_ranking_indices).map(self.idx2bcl)
        
        if len(keyword_candidates) == 0:
            return query_ranking.iloc[:self.num_suggestions].values

        ranking_filter = pd.Series(True, index=np.arange(len(query_ranking)))
        for candidates in keyword_candidates:
            subfilter = pd.Series(False, index=np.arange(len(query_ranking)))
            for candidate in candidates:
                subfilter = subfilter | query_ranking.str.contains(candidate)
            ranking_filter = ranking_filter & subfilter

        return query_ranking[ranking_filter].iloc[:self.num_suggestions].values
