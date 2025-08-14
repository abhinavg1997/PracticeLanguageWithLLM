package com.serenity_temp.example.conv.actors;

import akka.actor.typed.Behavior;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.*;
import com.serenity_temp.example.conv.protocol.Messages;

/**
 * Calls local LLM HTTP service (/chat) with JSON {targetLang, history, latestUser}.
 */
public class LlmClientActor extends AbstractBehavior<Object> {

    public static Behavior<Object> create() {
        return Behaviors.setup(ctx -> new LlmClientActor(ctx));
    }

    public static final class Generate {
        public final Messages.LlmRequest req;
        public final ActorRef<Messages.Msg> replyTo;
        public final ActorRef<ConsoleActor.Command> console;
        public Generate(Messages.LlmRequest req, ActorRef<Messages.Msg> replyTo, ActorRef<ConsoleActor.Command> console) { 
            this.req = req; 
            this.replyTo = replyTo; 
            this.console = console;
        }
    }

    // Note: HTTP client and JSON mapper commented for future real HTTP
    // private static final okhttp3.MediaType JSON = okhttp3.MediaType.parse("application/json; charset=utf-8");
    // private final okhttp3.OkHttpClient http;
    // private final com.fasterxml.jackson.databind.ObjectMapper om;

    private LlmClientActor(ActorContext<Object> ctx) {
        super(ctx);
        // this.http = new okhttp3.OkHttpClient.Builder().build();
        // this.om = new com.fasterxml.jackson.databind.ObjectMapper();
    }

    @Override
    public Receive<Object> createReceive() {
        return newReceiveBuilder()
            .onMessage(Generate.class, this::onGenerate)
            .build();
    }

	/*
	private Behavior<Object> onGenerate(Generate gen) {
		// Real HTTP implementation commented for offline testing
		try {
			String json = om.writeValueAsString(gen.req);
			Request req = new Request.Builder()
				.url("http://127.0.0.1:8000/chat")
				.post(RequestBody.create(json, JSON))
				.build();
			http.newCall(req).enqueue(new Callback() {
				@Override public void onFailure(Call call, IOException e) {
					session.tell(new Messages.LlmError(e.toString()));
				}
				@Override public void onResponse(Call call, Response resp) throws IOException {
					try (ResponseBody body = resp.body()) {
						if (!resp.isSuccessful()) {
							session.tell(new Messages.LlmError("HTTP " + resp.code()));
							return;
						}
						String s = body != null ? body.string() : "";
						String reply = om.readTree(s).get("text").asText();
						session.tell(new Messages.LlmReply(reply));
					}
				}
			});
		} catch (Exception e) {
			session.tell(new Messages.LlmError(e.toString()));
		}
		return this;
	}
	*/

	    private Behavior<Object> onGenerate(Generate gen) {
        Messages.LlmRequest r = gen.req;
        String reply = "[FAKE] (" + r.targetLang() + ") " + r.latestUser();
        gen.console.tell(new ConsoleActor.Print("[DEBUG] LlmClientActor responding: " + reply));
        gen.replyTo.tell(new Messages.LlmReply(reply, gen.console));
        return this;
    }
}


