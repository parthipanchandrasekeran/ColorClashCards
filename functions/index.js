const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.firestore();

const STALE_THRESHOLD_HOURS = 6;
const BATCH_SIZE = 500;

/**
 * Delete all documents matching a query, including known subcollections.
 * Returns { docsDeleted, subDocsDeleted }.
 */
async function deleteQueryResults(query, subcollections, label) {
  let docsDeleted = 0;
  let subDocsDeleted = 0;

  while (true) {
    const snapshot = await query.limit(BATCH_SIZE).get();
    if (snapshot.empty) break;

    const docIds = snapshot.docs.map((d) => d.id);
    functions.logger.info(`[${label}] Deleting batch of ${docIds.length} docs`, { docIds });

    const batch = db.batch();
    for (const doc of snapshot.docs) {
      // Delete known subcollections first
      for (const sub of subcollections) {
        const subSnap = await doc.ref.collection(sub).limit(BATCH_SIZE).get();
        if (!subSnap.empty) {
          const subBatch = db.batch();
          subSnap.docs.forEach((subDoc) => subBatch.delete(subDoc.ref));
          await subBatch.commit();
          subDocsDeleted += subSnap.docs.length;
          functions.logger.info(`[${label}] Deleted ${subSnap.docs.length} docs from ${doc.id}/${sub}`);
        }
      }
      batch.delete(doc.ref);
    }

    await batch.commit();
    docsDeleted += snapshot.docs.length;

    if (snapshot.docs.length < BATCH_SIZE) break;
  }

  return { docsDeleted, subDocsDeleted };
}

/**
 * Scheduled function: runs every hour to clean up stale rooms.
 *
 * Deletes:
 * 1. Card game rooms older than 6 hours
 * 2. Ludo rooms older than 6 hours
 * 3. Any rooms with status "ENDED" (game is over)
 */
exports.cleanupStaleRooms = functions.pubsub
  .schedule("every 1 hours")
  .onRun(async () => {
    const now = admin.firestore.Timestamp.now();
    const cutoff = admin.firestore.Timestamp.fromMillis(
      now.toMillis() - STALE_THRESHOLD_HOURS * 60 * 60 * 1000
    );

    functions.logger.info("Room cleanup started", {
      cutoff: cutoff.toDate().toISOString(),
      thresholdHours: STALE_THRESHOLD_HOURS,
    });

    const cardSubcollections = ["match", "actions", "voiceChat", "presence", "gameLogs"];
    const ludoSubcollections = ["presence"];
    const snlSubcollections = ["presence"];

    try {
      // 1. Stale card game rooms (createdAt < 6 hours ago)
      const staleCardRooms = db
        .collection("rooms")
        .where("createdAt", "<", cutoff);
      const cardResult = await deleteQueryResults(
        staleCardRooms,
        cardSubcollections,
        "stale-card-rooms"
      );

      // 2. Stale Ludo rooms (createdAt < 6 hours ago)
      const staleLudoRooms = db
        .collection("ludoRooms")
        .where("createdAt", "<", cutoff);
      const ludoResult = await deleteQueryResults(
        staleLudoRooms,
        ludoSubcollections,
        "stale-ludo-rooms"
      );

      // 3. Ended card game rooms (any age)
      const endedCardRooms = db
        .collection("rooms")
        .where("status", "==", "ENDED");
      const endedCardResult = await deleteQueryResults(
        endedCardRooms,
        cardSubcollections,
        "ended-card-rooms"
      );

      // 4. Ended Ludo rooms (any age)
      const endedLudoRooms = db
        .collection("ludoRooms")
        .where("status", "==", "ENDED");
      const endedLudoResult = await deleteQueryResults(
        endedLudoRooms,
        ludoSubcollections,
        "ended-ludo-rooms"
      );

      // 5. Stale SNL rooms (createdAt < 6 hours ago)
      const staleSnlRooms = db
        .collection("snlRooms")
        .where("createdAt", "<", cutoff);
      const snlResult = await deleteQueryResults(
        staleSnlRooms,
        snlSubcollections,
        "stale-snl-rooms"
      );

      // 6. Ended SNL rooms (any age)
      const endedSnlRooms = db
        .collection("snlRooms")
        .where("status", "==", "ENDED");
      const endedSnlResult = await deleteQueryResults(
        endedSnlRooms,
        snlSubcollections,
        "ended-snl-rooms"
      );

      functions.logger.info("Room cleanup complete", {
        staleCardRooms: cardResult.docsDeleted,
        staleCardSubDocs: cardResult.subDocsDeleted,
        staleLudoRooms: ludoResult.docsDeleted,
        staleLudoSubDocs: ludoResult.subDocsDeleted,
        endedCardRooms: endedCardResult.docsDeleted,
        endedCardSubDocs: endedCardResult.subDocsDeleted,
        endedLudoRooms: endedLudoResult.docsDeleted,
        endedLudoSubDocs: endedLudoResult.subDocsDeleted,
        staleSnlRooms: snlResult.docsDeleted,
        staleSnlSubDocs: snlResult.subDocsDeleted,
        endedSnlRooms: endedSnlResult.docsDeleted,
        endedSnlSubDocs: endedSnlResult.subDocsDeleted,
      });
    } catch (error) {
      functions.logger.error("Room cleanup failed", {
        error: error.message,
        stack: error.stack,
      });
      throw error;
    }

    return null;
  });
