package Controller;

import Configuration.Config;
import Model.AsyncClientMaster;
import Model.AsyncVotingStore;
import Model.ClientMaster;
import Model.VotingDB;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.templ.JadeTemplateEngine;

/**
 * @author Robin Duda
 *         <p/>
 *         Webserver for the API.
 */
public class WebServer implements Verticle {
    private AsyncVotingStore votings;
    private AsyncClientMaster client;
    private Vertx vertx;

    public WebServer() {
    }

    public WebServer(AsyncVotingStore votings, AsyncClientMaster client) {
        this.votings = votings;
    }

    @Override
    public void init(Vertx vertx, Context context) {
        this.vertx = vertx;

        if (votings == null) {
            votings = new VotingDB(
                    MongoClient.createShared(vertx,
                            new JsonObject()
                                    .put("connection_string", Config.CONNECTION_STRING)
                                    .put("db_name", Config.DB_NAME)
                    )
            );
        }

        if (client == null)
            client = new ClientMaster(vertx);
    }

    @Override
    public void start(Future<Void> future) throws Exception {
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());

        new APIRouter(router, votings, client);
        setTemplating(router);
        setResources(router);
        setCatchAll(router);

        server.requestHandler(router::accept).listen(Config.WEB_PORT);
        future.complete();
    }

    @Override
    public void stop(Future<Void> future) throws Exception {
        future.complete();
    }

    private void setTemplating(Router router) {
        JadeTemplateEngine jade = JadeTemplateEngine.create();

        router.route("/").handler(context -> {
            jade.render(context, "templates/index", result -> {
                if (result.succeeded())
                    context.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/html").end(result.result());
                else
                    context.fail(result.cause());
            });
        });
    }

    private void setResources(Router router) {
        router.route("/resources/*").handler(StaticHandler.create()
                .setCachingEnabled(true));
    }

    private void setCatchAll(Router router) {
        router.route().handler(context -> {
            HttpServerResponse response = context.response();
            response.setStatusCode(404);
            response.putHeader("content-type", "application/json");
            response.end("{\"page\" : 404}");
        });
    }

    @Override
    public Vertx getVertx() {
        return vertx;
    }
}
