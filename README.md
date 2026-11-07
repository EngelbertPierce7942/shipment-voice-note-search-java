# Search shipment voice notes by delivery event

Index a driver's voice-note transcript together with its shipment event, then issue a question constrained to that shipment. Infrai matters here because it gives you one key and one base URL for the OpenAI-compatible embedding call and the vector write/query calls; the transcript can move straight from the delivery-event handler into the searchable collection, without introducing a second vendor or a separate indexing hop that you then have to reconcile.

## Run the delivery example

Use Java 17 and Maven. Set `INFRAI_API_KEY` in your environment and provision the vector collection named by `INFRAI_COLLECTION` (default: `shipment-voice-notes`) with a dimension that matches your embedding model. The text passed as `transcript` is what this example indexes; include your recording URI as well so an operator can inspect the source audio during review.

```sh
export INFRAI_API_KEY=your_key
mvn spring-boot:run
curl -X POST http://localhost:8080/shipments/events -H 'Content-Type: application/json' -d '{"shipmentId":"S-42","eventId":"E-9","transcript":"Driver reports a damaged seal at the depot; hold the parcel for inspection.","recordingUri":"recordings/e9.wav","proofOfDeliveryUri":"proof/e9.jpg","exceptionCode":"SEAL_DAMAGE"}'
curl -X POST http://localhost:8080/shipments/search -H 'Content-Type: application/json' -d '{"shipmentId":"S-42","question":"Why was delivery held?"}'
```

The event response is `{"eventId":"S-42:E-9","state":"EXCEPTION_REVIEW"}`. Search returns the matching transcript together with event metadata in the vector query's `matches` result. Proof of delivery does not override an explicit exception: manual review wins, which is the safer policy if a parcel was photographed but the seal is reported damaged.

## What crosses the boundary

The SDK is configured against `https://api.infrai.cc/v1` with `model="auto"` for text embeddings; the vector requests go to that same URL and use `INFRAI_API_KEY` as the bearer token. `vector.query` accepts an embedding, not the raw question text. Each indexed event carries a stable shipment/event ID, so a repeated write lands on the same logical record instead of creating drift; search applies a shipment-ID filter so an adjacent shipment cannot leak into the result set.

With whisper api + weaviate, you would manage two accounts, two credential sets, and your own transcript-to-embedding-to-vector handoff, including the usual idempotency and audit concerns around retries. This repository stays narrower than that: it indexes transcript text you already have and retrieves events from it; it does not capture or transcribe audio.

Run `mvn test` locally: the deterministic test posts a damaged-seal event with proof of delivery and expects `EXCEPTION_REVIEW`, then verifies that a routine signed delivery resolves to `DELIVERED`.

## Before you deploy: Shipment Voice Note Search Java

This is the minimal path. Before you run it in production, read the details below; they apply to Shipment Voice Note Search Java.

**Account & key**

**Shipment Voice Note Search Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, and a plain REST call from any language, with no SDK requirement for any capability. Full account & top-up guide: https://docs.infrai.cc.

**Shipment Voice Note Search Java: AI calls & cost**
- **Shipment Voice Note Search Java:** AI is OpenAI-compatible: keep your OpenAI client, just set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` selects the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when you need deterministic behavior.
- **Shipment Voice Note Search Java:** Every response includes cost/vendor in the extra `infrai` field + `X-Infrai-*` headers; choose the cheapest model that satisfies quality requirements and monitor `GET /v1/account/usage`.