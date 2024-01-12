package com.mongodb.atlas;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.search.CompoundSearchOperator;
import com.mongodb.client.model.search.SearchCount;
import com.mongodb.client.model.search.SearchOperator;
import com.mongodb.client.model.search.SearchPath;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static com.mongodb.client.model.Aggregates.limit;
import static com.mongodb.client.model.Aggregates.project;
import static com.mongodb.client.model.Aggregates.search;
import static com.mongodb.client.model.Aggregates.skip;
import static com.mongodb.client.model.Projections.excludeId;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Projections.meta;
import static com.mongodb.client.model.Projections.metaSearchScore;
import static com.mongodb.client.model.search.SearchOptions.searchOptions;

public class SearchServlet extends HttpServlet {
  private MongoCollection<Document> collection;
  private String indexName;

  private Logger logger;

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);

    logger = Logger.getLogger(config.getServletName());

    String uri = System.getenv("ATLAS_URI");
    if (uri == null) {
      throw new ServletException("ATLAS_URI must be specified");
    }

    String databaseName = config.getInitParameter("database");
    String collectionName = config.getInitParameter("collection");
    indexName = config.getInitParameter("index");

    MongoClient mongo_client = MongoClients.create(uri);
    MongoDatabase database = mongo_client.getDatabase(databaseName);
    collection = database.getCollection(collectionName);

    logger.info("Servlet " + config.getServletName() + " initialized: " + databaseName + " / " + collectionName + " / " + indexName);
  }

  /**
   * @param request  an {@link HttpServletRequest} object that contains the request the client has made of the servlet
   * @param response an {@link HttpServletResponse} object that contains the response the servlet sends to the client
   *
   * <p>
   *    /path?q=&lt;query&gt;
   *         &search=&lt;fields to search&gt;
   *         [&skip=N]
   *         [&limit=X]
   *         [&project=&lt;fields to return&gt;]
   *         [&filter=genres:Adventure&filter=&lt;field_name&gt;:&lt;field_value&gt;]
   *         [&debug=true]
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String q = request.getParameter("q");
    String searchFieldsValue = request.getParameter("search");
    String limitValue = request.getParameter("limit");
    String skipValue = request.getParameter("skip");
    String projectFieldsValue = request.getParameter("project");
    String debugValue = request.getParameter("debug");
    String[] filters = request.getParameterMap().get("filter");

    // Validate params
    int limit = Math.min(25, limitValue == null ? 10 : Integer.parseInt(limitValue));
    int skip = Math.min(100, skipValue == null ? 0 : Integer.parseInt(skipValue));
    boolean debug = Boolean.parseBoolean(debugValue);

    if (q == null || q.length() == 0) {
      response.sendError(400, "`q` is missing");
      return;
    }
    if (searchFieldsValue == null) {
      response.sendError(400, "`search` fields-list required");
      return;
    }

    List<SearchOperator> filterOperators = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    if (filters != null) {
      for (String filter : filters) {
        int c = filter.indexOf(':');

        if (c == -1) {
          errors.add("Invalid `filter`: " + filter);
        } else {
          filterOperators.add(SearchOperator.of(
              new Document("equals",
                  new Document("path", filter.substring(0,c))
                      .append("value", filter.substring(c+1)))
          ));
        }
      }
    }

    if (errors.size() > 0) {
      response.sendError(400, errors.toString());
      return;
    }

    String[] searchFields = searchFieldsValue.split(",");

    List<String> projectFields = new ArrayList<>();
    if (projectFieldsValue != null) {
      projectFields.addAll(List.of(projectFieldsValue.split(",")));
    }

    boolean include_id = false;
    if (projectFields.contains("_id")) {
      include_id = true;
      projectFields.remove("_id");
    }

    boolean includeScore = false;
    if (projectFields.contains("_score")) {
      includeScore = true;
      projectFields.remove("_score");
    }

    // $search
    List<SearchPath> searchPath = new ArrayList<>();
    for (String search_field : searchFields) {
      searchPath.add(SearchPath.fieldPath(search_field));
    }

    CompoundSearchOperator operator = SearchOperator.compound()
        .must(List.of(SearchOperator.text(searchPath, List.of(q))));
    if (filterOperators.size() > 0)
      operator = operator.filter(filterOperators);

    Bson searchStage = search(
        operator,
        searchOptions()
            .option("scoreDetails", debug)
            .index(indexName)
            .count(SearchCount.total())
    );

    // $project
    List<Bson> projections = new ArrayList<>();
    projections.add(include(projectFields));
    if (include_id) {
      projections.add(include("_id"));
    } else {
      projections.add(excludeId());
    }
    if (debug) {
      projections.add(meta("_scoreDetails", "searchScoreDetails"));
    }
    if (includeScore) {
      projections.add(metaSearchScore("_score"));
    }
    Bson projection = fields(projections);

    // Using $facet stage to provide both the documents and $$SEARCH_META data.
    // The $$SEARCH_META data contains the total matching document count, etc
    Bson facetStage = new Document("$facet",
      new Document("docs",
        Arrays.asList(skip(skip), limit(limit), project(projection)))
      .append("meta",
        Arrays.asList(new Document("$replaceWith", "$$SEARCH_META"), limit(1)))
    );

    AggregateIterable<Document> aggregationResults = collection.aggregate(List.of(
        searchStage,
        facetStage
    ));

    Document responseDoc = new Document();
    responseDoc.put("request", new Document()
        .append("q", q)
        .append("skip", skip)
        .append("limit", limit)
        .append("search", searchFieldsValue)
        .append("project", projectFieldsValue)
        .append("filter", filters==null ? Collections.EMPTY_LIST : List.of(filters)));

    if (debug) {
      responseDoc.put("debug", aggregationResults.explain().toBsonDocument());
    }

    // When using $facet stage, only one "document" is returned,
    // containing the keys specified above: "docs" and "meta"
    Document results = aggregationResults.first();
    if (results != null) {
      for (String s : results.keySet()) {
        responseDoc.put(s,results.get(s));
      }
    }

    response.setContentType("text/json");
    PrintWriter writer = response.getWriter();
    writer.println(responseDoc.toJson());
    writer.close();

    logger.info(request.getServletPath() + "?" + request.getQueryString());
  }
}
