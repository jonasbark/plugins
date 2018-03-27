// Copyright 2017, the Chromium project authors.  Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

part of cloud_firestore;

/// A CollectionReference object can be used for adding documents, getting
/// document references, and querying for documents (using the methods
/// inherited from [Query]).
class CollectionReference extends Query {
  CollectionReference._({@required Firestore firestore, @required List<String> pathComponents, Map<String, dynamic> parameters})
      : super._(firestore: firestore, pathComponents: pathComponents, parameters: parameters);

  /// For subcollections, parent returns the containing DocumentReference.
  ///
  /// For root collections, null is returned.
  CollectionReference parent() {
    if (_pathComponents.isEmpty) {
      return null;
    }
    return new CollectionReference._(
        _firestore, (new List<String>.from(_pathComponents)..removeLast()));
  }

  CollectionReference _copyWithParameters(Map<String, dynamic> parameters) {
    return new CollectionReference._(
      firestore: _firestore,
      pathComponents: _pathComponents,
      parameters: new Map<String, dynamic>.unmodifiable(
        new Map<String, dynamic>.from(_parameters)..addAll(parameters),
      ),
    );
  }

  /// Create a query constrained to only return child nodes with a value greater
  /// than or equal to the given value, using the given orderBy directive or
  /// priority as default, and optionally only child nodes with a key greater
  /// than or equal to the given key.
  CollectionReference startAt(dynamic value, {String key}) {
    assert(!_parameters.containsKey('startAt'));
    assert(value is String || value is bool || value is double || value is int);
    final Map<String, dynamic> parameters = <String, dynamic>{'startAt': value};
    if (key != null) parameters['startAtKey'] = key;
    return _copyWithParameters(parameters);
  }

  /// Create a query constrained to only return child nodes with a value less
  /// than or equal to the given value, using the given orderBy directive or
  /// priority as default, and optionally only child nodes with a key less
  /// than or equal to the given key.
  CollectionReference endAt(dynamic value, {String key}) {
    assert(!_parameters.containsKey('endAt'));
    assert(value is String || value is bool || value is double || value is int);
    final Map<String, dynamic> parameters = <String, dynamic>{'endAt': value};
    if (key != null) parameters['endAtKey'] = key;
    return _copyWithParameters(parameters);
  }

  /// Create a query constrained to only return child nodes with the given
  /// `value` (and `key`, if provided).
  ///
  /// If a key is provided, there is at most one such child as names are unique.
  CollectionReference equalTo(dynamic value, {String key}) {
//    assert(!_parameters.containsKey('equalTo'));
    assert(value is String || value is bool || value is double || value is int);

    List<String> equalTos;
    List<String> equalToKeys;
    if (_parameters.containsKey('equalTo')) {
      equalTos = _parameters['equalTo'];
      equalTos.add(value);
      equalToKeys = _parameters['equalToKey'];
      equalToKeys.add(key ?? null);
    }
    else {
      equalTos = [value];
      equalToKeys = [key ?? null];
    }

    return _copyWithParameters(
      <String, dynamic>{'equalTo': equalTos, 'equalToKey': equalToKeys},
    );
  }

  /// Create a query with limit and anchor it to the start of the window.
  CollectionReference limit(int limit) {
    assert(!_parameters.containsKey('limit'));
    return _copyWithParameters(<String, dynamic>{'limit': limit});
  }

  /// Generate a view of the data sorted by values of a particular child key.
  ///
  /// Intended to be used in combination with [startAt], [endAt], or
  /// [equalTo].
  CollectionReference orderByChild(String key) {
    assert(key != null);
    assert(!_parameters.containsKey('orderBy'));
    return _copyWithParameters(
      <String, dynamic>{'orderBy': 'child', 'orderByChildKey': key},
    );
  }

  /// Generate a view of the data sorted by key.
  ///
  /// Intended to be used in combination with [startAt], [endAt], or
  /// [equalTo].
  CollectionReference orderByKey() {
    assert(!_parameters.containsKey('orderBy'));
    return _copyWithParameters(<String, dynamic>{'orderBy': 'key'});
  }

  /// Generate a view of the data sorted by value.
  ///
  /// Intended to be used in combination with [startAt], [endAt], or
  /// [equalTo].
  CollectionReference orderByValue() {
    assert(!_parameters.containsKey('orderBy'));
    return _copyWithParameters(<String, dynamic>{'orderBy': 'value'});
  }

  /// Generate a view of the data sorted by priority.
  ///
  /// Intended to be used in combination with [startAt], [endAt], or
  /// [equalTo].
  CollectionReference orderByPriority() {
    assert(!_parameters.containsKey('orderBy'));
    return _copyWithParameters(<String, dynamic>{'orderBy': 'priority'});
  }


  /// Returns a `DocumentReference` with the provided path.
  ///
  /// If no [path] is provided, an auto-generated ID is used.
  ///
  /// The unique key generated is prefixed with a client-generated timestamp
  /// so that the resulting list will be chronologically-sorted.
  DocumentReference document([String path]) {
    List<String> childPath;
    if (path == null) {
      final String key = PushIdGenerator.generatePushChildName();
      childPath = new List<String>.from(_pathComponents)..add(key);
    } else {
      childPath = new List<String>.from(_pathComponents)
        ..addAll(path.split(('/')));
    }
    return new DocumentReference._(_firestore, childPath);
  }

  /// Returns a `DocumentReference` with an auto-generated ID, after
  /// populating it with provided [data].
  ///
  /// The unique key generated is prefixed with a client-generated timestamp
  /// so that the resulting list will be chronologically-sorted.
  Future<DocumentReference> add(Map<String, dynamic> data) async {
    final DocumentReference newDocument = document();
    await newDocument.setData(data);
    return newDocument;
  }
}
