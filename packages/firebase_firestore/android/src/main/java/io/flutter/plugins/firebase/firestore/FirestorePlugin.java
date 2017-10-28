// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.firebase.firestore;

import android.util.SparseArray;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** FirebaseDatabasePlugin */
public class FirestorePlugin implements MethodCallHandler {

    public static final String TAG = "FirestorePlugin";
    private final MethodChannel channel;

    // Handles are ints used as indexes into the sparse array of active observers
    private int nextHandle = 0;
    private final SparseArray<EventObserver> observers = new SparseArray<>();
    private final SparseArray<DocumentObserver> documentObservers = new SparseArray<>();
    private final SparseArray<ListenerRegistration> listenerRegistrations = new SparseArray<>();

    public static void registerWith(PluginRegistry.Registrar registrar) {
        final MethodChannel channel =
                new MethodChannel(registrar.messenger(), "plugins.flutter.io/firebase_firestore");
        channel.setMethodCallHandler(new FirestorePlugin(channel));
    }

    private FirestorePlugin(MethodChannel channel) {
        this.channel = channel;
    }

    private CollectionReference getCollectionReference(Map<String, Object> arguments) {
        String path = (String) arguments.get("path");
        return FirebaseFirestore.getInstance().collection(path);
    }

    private DocumentReference getDocumentReference(Map<String, Object> arguments) {
        String path = (String) arguments.get("path");
        return FirebaseFirestore.getInstance().document(path);
    }

    private Query getQuery(Map<String, Object> arguments) {
        Query query = getCollectionReference(arguments);

        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) arguments.get("parameters");
        if (parameters == null) return query;
        Object orderBy = parameters.get("orderBy");
        if ("key".equals(orderBy)) {
            query = query.orderBy((String) parameters.get("orderByKey"));
            //TODO direction
        }
        if (parameters.containsKey("startAt")) {
            Object startAt = parameters.get("startAt");
            if (parameters.containsKey("startAtKey")) {
                String startAtKey = (String) parameters.get("startAtKey");
                if (startAt instanceof Boolean) {
                    query = query.startAt((Boolean) startAt, startAtKey);
                } else if (startAt instanceof String) {
                    query = query.startAt((String) startAt, startAtKey);
                } else {
                    query = query.startAt(((Number) startAt).doubleValue(), startAtKey);
                }
            } else {
                if (startAt instanceof Boolean) {
                    query = query.startAt((Boolean) startAt);
                } else if (startAt instanceof String) {
                    query = query.startAt((String) startAt);
                } else {
                    query = query.startAt(((Number) startAt).doubleValue());
                }
            }
        }
        if (parameters.containsKey("endAt")) {
            Object endAt = parameters.get("endAt");
            if (parameters.containsKey("endAtKey")) {
                String endAtKey = (String) parameters.get("endAtKey");
                if (endAt instanceof Boolean) {
                    query = query.endAt((Boolean) endAt, endAtKey);
                } else if (endAt instanceof String) {
                    query = query.endAt((String) endAt, endAtKey);
                } else {
                    query = query.endAt(((Number) endAt).doubleValue(), endAtKey);
                }
            } else {
                if (endAt instanceof Boolean) {
                    query = query.endAt((Boolean) endAt);
                } else if (endAt instanceof String) {
                    query = query.endAt((String) endAt);
                } else {
                    query = query.endAt(((Number) endAt).doubleValue());
                }
            }
        }
        if (parameters.containsKey("equalTo")) {
            Object equalTo = parameters.get("equalTo");
            if (equalTo instanceof Boolean || equalTo instanceof String) {
                query = query.whereEqualTo((String) parameters.get("equalToKey"), equalTo);
            } else {
                query = query.whereEqualTo((String) parameters.get("equalToKey"), ((Number) equalTo).doubleValue());
            }
        }
        if (parameters.containsKey("limit")) {
            query = query.limit((long) parameters.get("limit"));
        }
        return query;
    }

    private class DocumentObserver implements EventListener<DocumentSnapshot> {
        private int handle;

        DocumentObserver(int handle) {
            this.handle = handle;
        }

        @Override
        public void onEvent(DocumentSnapshot documentSnapshot, FirebaseFirestoreException e) {
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("handle", handle);
            arguments.put("path", documentSnapshot.getId());
            if (documentSnapshot.exists()) {
                arguments.put("data", convertReferencesToPath(documentSnapshot.getData()));
            } else {
                arguments.put("data", null);
            }
            channel.invokeMethod("DocumentSnapshot", arguments);
        }
    }

    private class EventObserver implements EventListener<QuerySnapshot> {
        private int handle;

        EventObserver(int handle) {
            this.handle = handle;
        }

        @Override
        public void onEvent(QuerySnapshot querySnapshot, FirebaseFirestoreException e) {
            Map<String, Object> arguments = new HashMap<>();
            arguments.put("handle", handle);

            List<Map<String, Object>> documents = new ArrayList<>();
            for (DocumentSnapshot document : querySnapshot.getDocuments()) {
                Map<String, Object> obj = new HashMap<>();
                obj.put("path", document.getId());
                obj.put("document", convertReferencesToPath(document.getData()));
                documents.add(obj);
            }
            arguments.put("documents", documents);

            List<Map<String, Object>> documentChanges = new ArrayList<>();
            for (DocumentChange documentChange : querySnapshot.getDocumentChanges()) {
                Map<String, Object> change = new HashMap<>();
                String type = null;
                switch (documentChange.getType()) {
                    case ADDED:
                        type = "DocumentChangeType.added";
                        break;
                    case MODIFIED:
                        type = "DocumentChangeType.modified";
                        break;
                    case REMOVED:
                        type = "DocumentChangeType.removed";
                        break;
                }
                change.put("type", type);
                change.put("path", documentChange.getDocument().getId());
                change.put("oldIndex", documentChange.getOldIndex());
                change.put("newIndex", documentChange.getNewIndex());
                change.put("document", convertReferencesToPath(documentChange.getDocument().getData()));
                documentChanges.add(change);
            }
            arguments.put("documentChanges", documentChanges);

            channel.invokeMethod("QuerySnapshot", arguments);
        }
    }

    private Object convertReferencesToPath(final Map<String, Object> data) {
        Set<String> keys = data.keySet();
        for (final String key : keys) {
            if (data.get(key) instanceof DocumentReference) { //not supported => replace by path
                data.put(key, ((DocumentReference) data.get(key)).getPath());
            }
        }
        return data;
    }

    @Override
    public void onMethodCall(MethodCall call, final Result result) {
        switch (call.method) {
            case "Query#addSnapshotListener":
            {
                Map<String, Object> arguments = call.arguments();
                int handle = nextHandle++;
                EventObserver observer = new EventObserver(handle);
                observers.put(handle, observer);
                listenerRegistrations.put(handle, getQuery(arguments).addSnapshotListener(observer));
                result.success(handle);
                break;
            }
            case "Query#addDocumentListener":
            {
                Map<String, Object> arguments = call.arguments();
                int handle = nextHandle++;
                DocumentObserver observer = new DocumentObserver(handle);
                documentObservers.put(handle, observer);
                listenerRegistrations.put(
                        handle, getDocumentReference(arguments).addSnapshotListener(observer));
                result.success(handle);
            }
            case "Query#removeListener":
            {
                Map<String, Object> arguments = call.arguments();
                // TODO(arthurthompson): find out why removeListener is sometimes called without handle.
                int handle = (Integer) arguments.get("handle");
                listenerRegistrations.get(handle).remove();
                listenerRegistrations.remove(handle);
                observers.remove(handle);
                result.success(null);
                break;
            }
            case "DocumentReference#setData":
            {
                Map<String, Object> arguments = call.arguments();
                DocumentReference documentReference = getDocumentReference(arguments);
                documentReference.set(arguments.get("data"));
                result.success(null);
                break;
            }
            case "DocumentReference#delete":
            {
                Map<String, Object> arguments = call.arguments();
                DocumentReference documentReference = getDocumentReference(arguments);
                documentReference.delete();
                result.success(null);
                break;
            }
            default:
            {
                result.notImplemented();
                break;
            }
        }
    }
}
