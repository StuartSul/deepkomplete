package com.zai.deepkomplete;

import com.zai.deepkomplete.DeepKomplete;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class Example {

    public static void main(String[] args) {

        // 인스턴스를 생성합니다 (몇 초 소요)
        DeepKomplete dk = null;
        try {
            dk = new DeepKomplete();
        } catch (IOException e) { }

        // 예시로 사용할 현재 검색어 및 과거 검색어들을 만듭니다
        String query = "가";
        List<String> history = Arrays.asList(
            "폴로", "셔츠", "맨투맨", "폴로", "폴로", "구찌 스니커즈 라이톤", 
            "아미", "구찌 스니커즈 라이톤아미", "구찌 스니커즈 라이톤", "구찌 스니커즈 라이톤"
        );

        // 추천 검색어를 생성합니다 (첫 request 이후부터 1ms~2ms 소요)
        List<String> suggestions = dk.suggest(query, history);

        // 결과를 터미널에 출력합니다
        System.out.println("\nQuery: " + query);
        System.out.println("\nHistory (Recent First): ");
        for (int i = 0; i < history.size(); i++) 
            System.out.print(history.get(i) + (i == history.size() - 1 ? "" : " | "));
        System.out.println("\n\nSuggestions: ");
        for (int i = 0; i < suggestions.size(); i++) 
            System.out.print(suggestions.get(i) + (i == suggestions.size() - 1 ? "" : " | "));
        System.out.print("\n\n");
    }
}
