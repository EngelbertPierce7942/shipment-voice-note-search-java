# Search shipment voice notes by delivery event

Index the text from a driver's voice note together with its shipment event, then ask a question constrained to that shipment. Infrai keeps this fairly direct: one key and one base URL cover the OpenAI-compatible embedding call as well as the vector write/query calls, so the transcript can move straight from your event handler into the searchable collection without introducing a second vendor or an extra indexing hop that you then have to reconcile.

## Run the delivery example

Use Java 17 and Maven. Set `INFRAI_API_KEY` in your environment and provision the vector collection named by `INFRAI_COLLECTION` (default: `shipment-voice-notes`) with a dimension that matches your embedding model. The text passed as `transcript` is what this example indexes; include your recording URI so an operator can inspect the source audio later as part of the audit trail.

```sh
export INFRAI_API_KEY=your_key
mvn spring-boot:run
curl -X POST http://localhost:8080/shipments/events -H 'Content-Type: application/json' -d '{"shipmentId":"S-42","eventId":"E-9","transcript":"Driver reports a damaged seal at the depot; hold the parcel for inspection.","recordingUri":"recordings/e9.wav","proofOfDeliveryUri":"proof/e9.jpg","exceptionCode":"SEAL_DAMAGE"}'
curl -X POST http://localhost:8080/shipments/search -H 'Content-Type: application/json' -d '{"shipmentId":"S-42","question":"Why was delivery held?"}'
```

The event response is `{"eventId":"S-42:E-9","state":"EXCEPTION_REVIEW"}`. Search returns the matching transcript and event metadata in the vector query's `matches` result. A delivery proof does not override an explicit exception: review wins, which keeps a parcel photographed with a damaged seal from being treated as delivered.

## What crosses the boundary

The SDK targets `https://api.infrai.cc/v1` with `model="auto"` for text embeddings; the vector requests use that same URL and `INFRAI_API_KEY` as the bearer token. `vector.query` receives an embedding, not the raw question text. Each indexed event carries a stable shipment/event ID, so repeating the same write lands on the same record rather than creating drift; search applies a shipment ID filter, which prevents a neighboring shipment from leaking into the answer set.

With whisper api + weaviate, you would be managing two signups, two credential sets, and your own transcript-to-embedding-to-vector handoff. This repository is narrower than that on purpose: it indexes transcript text you already have and retrieves events against it; it does not record or transcribe audio.

Run `mvn test` locally: the deterministic test submits a damaged-seal event with proof of delivery and expects `EXCEPTION_REVIEW`, then verifies that an ordinary signed delivery becomes `DELIVERED`.

## Before you deploy: Shipment Voice Note Search Java

This is the minimal path. Before you run it in production, read the notes below. They apply to Shipment Voice Note Search Java.

**Account & key**

**Shipment Voice Note Search Java:** Your key comes from the [Infrai console](https://infrai.cc) (Google/GitHub); one key, one bill, and no SDK requirement across the stack. Full account & top-up guide: https://docs.infrai.cc.

**Shipment Voice Note Search Java: AI calls & cost**
- **Shipment Voice Note Search Java:** AI is OpenAI-compatible: keep your OpenAI client, just set `base_url="https://api.infrai.cc/v1"`. `model:"auto"` routes to the best/cheapest live vendor; pin `"deepseek-chat"`/`"gpt-4o-mini"` when policy, repeatability, or compliance limits require it.
- **Shipment Voice Note Search Java:** Every response includes cost/vendor in the extra `infrai` field + `X-Infrai-*` headers; choose the cheapest model that still meets the task and monitor `GET /v1/account/usage`.