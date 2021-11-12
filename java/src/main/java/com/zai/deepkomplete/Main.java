package com.zai.deepkomplete;

import com.zai.deepkomplete.DeepKomplete;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static spark.Spark.*;
 
import spark.Filter;
import spark.Request;
import spark.Response;
import spark.Route;

public class Main {

    static LinkedList<String> history = new LinkedList<String>();
    static DeepKomplete dk;

    public static void main(String[] args) {
        System.out.println("Loading DeepKomplete...");
        try {
            dk = new DeepKomplete();
        } catch (Exception e) { }
        System.out.println("Load complete");

        int port = 5000;
        port(port);
        enableCORS("*", "*", "*");

        post("/autocomplete", (req, res) -> autocomplete(req, res));
        post("/submit", (req, res) -> submit(req, res));
        post("/clear", (req, res) -> clear(req, res));

        memoryStats();
        System.out.println();
        System.out.println("Running on port " + port + "...");
    }

    private static String autocomplete(Request req, Response res) {
        JsonParser parser = new JsonParser();
        JsonElement element = parser.parse(req.body());
        String query = element.getAsJsonObject().get("query").getAsString();

        long startTime = System.nanoTime();
        List<String> suggestions = dk.suggest(query, history);
        long endTime = System.nanoTime();
        double totalTime = (endTime - startTime) / 1000000000.;
        System.out.println("Response Time: " + totalTime);

        res.type("application/json");
	    ArrayList suggestions_mapped = suggestions
            .stream().map(x -> "\"" + x + "\"")
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        return "{\"suggestions\":[" + String.join(", ", suggestions_mapped) + "]}";
    }

    private static String submit(Request req, Response res) {
        try {
            JsonParser parser = new JsonParser();
            JsonElement element = parser.parse(req.body());
            String query = element.getAsJsonObject().get("query").getAsString();

            history.addFirst(query);
            if (history.size() > 10)
                history.removeLast();
        } catch (Exception E) { }

        res.type("application/json");
	    ArrayList history_mapped = history
            .stream().map(x -> "\"" + x + "\"")
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        return "{\"history\":[" + String.join(", ", history_mapped) + "]}";
    }

    private static String clear(Request req, Response res) {
        history.clear();
        res.type("application/json");
	    ArrayList history_mapped = history
            .stream().map(x -> "\"" + x + "\"")
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        return "{\"history\":[" + String.join(", ", history_mapped) + "]}";
    }

    private static void enableCORS(final String origin, final String methods, final String headers) {
        options("/*", (request, response) -> {
            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }
            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }
            return "OK";
        });

        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", origin);
            response.header("Access-Control-Request-Method", methods);
            response.header("Access-Control-Allow-Headers", headers);
            response.type("application/json");
        });
    }

    private static void memoryStats() {
        int mb = 1024 * 1024;
        Runtime instance = Runtime.getRuntime();
        System.out.println("\n***** Heap utilization statistics [MB] *****");
        System.out.println("Total Memory : " + (instance.totalMemory() / mb) + "MB");
        System.out.println("Free Memory  : " + (instance.freeMemory() / mb) + "MB");
        System.out.println("Used Memory  : "
                + ((instance.totalMemory() - instance.freeMemory()) / mb) + "MB");
        System.out.println("Max Memory   : " + (instance.maxMemory() / mb) + "MB");
    }
}
