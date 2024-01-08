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
import java.util.logging.Level;
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
  private String index_name;

  private Logger logger;

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);

    logger = Logger.getLogger(config.getServletName());

    String uri = System.getenv("ATLAS_URI");
    if (uri == null) {
      throw new ServletException("ATLAS_URI must be specified");
    }

    String database_name = config.getInitParameter("database");
    String collection_name = config.getInitParameter("collection");
    index_name = config.getInitParameter("index");

    logger.log(Level.INFO, "Servlet Name: " + config.getServletName());
    logger.log(Level.INFO, "Context Path: " + config.getServletContext().getContextPath());
    logger.log(Level.INFO, "Servlet Context Name: " + config.getServletContext().getServletContextName());
    logger.log(Level.INFO, "Index Name: " + index_name);

    MongoClient mongo_client = MongoClients.create(uri);
    MongoDatabase database = mongo_client.getDatabase(database_name);
    collection = database.getCollection(collection_name);
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
    String search_fields_value = request.getParameter("search");
    String limit_value = request.getParameter("limit");
    String skip_value = request.getParameter("skip");
    String project_fields_value = request.getParameter("project");
    String debug_value = request.getParameter("debug");
    String[] filters = request.getParameterMap().get("filter");

    // Validate params
    List<String> errors = new ArrayList<>();
    int limit = Math.min(25, limit_value == null ? 10 : Integer.parseInt(limit_value));
    int skip = Math.min(100, skip_value == null ? 0 : Integer.parseInt(skip_value));
    boolean debug = Boolean.parseBoolean(debug_value);

    if (q == null || q.length() == 0) errors.add("`q` is missing");
    if (search_fields_value == null) errors.add("`search` fields-list required");

    List<SearchOperator> filter_operators = new ArrayList<>();
    if (filters != null) {
      for (String filter : filters) {
        int c = filter.indexOf(':');

        if (c == -1) {
          errors.add("Invalid `filter`: " + filter);
        } else {
          filter_operators.add(SearchOperator.of(
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

    String[] search_fields = search_fields_value.split(",");

    List<String> project_fields = new ArrayList<>();
    if (project_fields_value != null) {
      project_fields.addAll(List.of(project_fields_value.split(",")));
    }

    boolean include_id = false;
    if (project_fields.contains("_id")) {
      include_id = true;
      project_fields.remove("_id");
    }

    boolean include_score = false;
    if (project_fields.contains("_score")) {
      include_score = true;
      project_fields.remove("_score");
    }

    // $search
    List<SearchPath> search_path = new ArrayList<>();
    for (String search_field : search_fields) {
      search_path.add(SearchPath.fieldPath(search_field));
    }

    CompoundSearchOperator operator = SearchOperator.compound()
        .must(List.of(SearchOperator.text(search_path, List.of(q))));
    if (filter_operators.size() > 0)
      operator = operator.filter(filter_operators);

    Bson searchStage = search(
        operator,
        searchOptions()
            .option("scoreDetails", debug)
            .index(index_name)
            .count(SearchCount.total())
    );

    // $project
    List<Bson> projections = new ArrayList<>();
    projections.add(include(project_fields));
    if (include_id) {
      projections.add(include("_id"));
    } else {
      projections.add(excludeId());
    }
    if (debug) {
      projections.add(meta("_scoreDetails", "searchScoreDetails"));
    }
    if (include_score) {
      projections.add(metaSearchScore("_score"));
    }
    Bson projection = fields(projections);

    // Using $facet stage to provide both the documents and $$SEARCH_META data.
    // The $$SEARCH_META data contains the total matching document count, etc
    Bson facet_stage = new Document("$facet",
      new Document("docs",
        Arrays.asList(skip(skip), limit(limit), project(projection)))
      .append("meta",
        Arrays.asList(new Document("$replaceWith", "$$SEARCH_META"), limit(1)))
    );

    AggregateIterable<Document> aggregation_results = collection.aggregate(List.of(
        searchStage,
        facet_stage
    ));

    Document response_doc = new Document();
    response_doc.put("request", new Document()
        .append("q", q)
        .append("skip", skip)
        .append("limit", limit)
        .append("search", search_fields_value)
        .append("project", project_fields_value)
        .append("filter", filters==null ? Collections.EMPTY_LIST : List.of(filters)));

    if (debug) {
      response_doc.put("debug", aggregation_results.explain().toBsonDocument());
    }

    // When using $facet stage, only one "document" is returned,
    // containing the keys specified above: "docs" and "meta"
    Document results = aggregation_results.first();
    for (String s : results.keySet()) {
      response_doc.put(s,results.get(s));
    }

    response.setContentType("text/json");
    PrintWriter writer = response.getWriter();
    writer.println(response_doc.toJson());
    writer.close();
  }
}
