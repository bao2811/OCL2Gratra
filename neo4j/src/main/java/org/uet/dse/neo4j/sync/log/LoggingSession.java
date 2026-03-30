package org.uet.dse.neo4j.sync.log;

import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.uet.dse.neo4j.manager.WorkLogManager;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class LoggingSession implements Session {

    private final Session delegate;
    private static final WorkLogManager log = WorkLogManager.getInstance();

    public LoggingSession(Session delegate) {
        this.delegate = delegate;
    }
    @Override
    public Result run(String query, Value parameters) {
        WorkLogManager.getInstance()
            .log("Cypher Execute", "Query: " + query + " | Params: " + parameters);
        return delegate.run(query, parameters);
    }

    @Override
    public Result run(String query, Map<String, Object> parameters) {
        WorkLogManager.getInstance()
            .log("Cypher Execute", "Query: " + query + " | Params: " + parameters);
        return delegate.run(query, parameters);
    }

    @Override
    public Result run(String query) {
        WorkLogManager.getInstance()
            .log("Cypher Execute", "Query: " + query);
        return delegate.run(query);
    }

    @Override
    public Result run(String s, Record record) {
        WorkLogManager.getInstance()
            .log("Cypher Execute (Record)", "Query: " + s + " | Record: " + record);
        return delegate.run(s, record);
    }

    @Override
    public Result run(Query query) {
        WorkLogManager.getInstance()
            .log("Cypher Execute (Query object)", query.toString());
        return delegate.run(query);
    }



    @Override public void close() { delegate.close(); }
    @Override public boolean isOpen() { return delegate.isOpen(); }
    @Override public Transaction beginTransaction() { return delegate.beginTransaction(); }

    @Override
    public Transaction beginTransaction(TransactionConfig transactionConfig) {
        return null;
    }

    @Override public <T> T readTransaction(TransactionWork<T> work) { return delegate.readTransaction(work); }

    @Override
    public <T> T executeRead(TransactionCallback<T> callback) {
        return Session.super.executeRead(callback);
    }

    @Override
    public <T> T readTransaction(TransactionWork<T> transactionWork, TransactionConfig transactionConfig) {
        return null;
    }

    @Override
    public <T> T executeRead(TransactionCallback<T> transactionCallback, TransactionConfig transactionConfig) {
        return null;
    }

    @Override public <T> T writeTransaction(TransactionWork<T> work) { return delegate.writeTransaction(work); }

    @Override
    public <T> T executeWrite(TransactionCallback<T> callback) {
        return Session.super.executeWrite(callback);
    }

    @Override
    public void executeWriteWithoutResult(Consumer<TransactionContext> contextConsumer) {
        Session.super.executeWriteWithoutResult(contextConsumer);
    }

    @Override
    public <T> T writeTransaction(TransactionWork<T> transactionWork, TransactionConfig transactionConfig) {
        return null;
    }

    @Override
    public <T> T executeWrite(TransactionCallback<T> transactionCallback, TransactionConfig transactionConfig) {
        return null;
    }

    @Override
    public void executeWriteWithoutResult(Consumer<TransactionContext> contextConsumer, TransactionConfig config) {
        Session.super.executeWriteWithoutResult(contextConsumer, config);
    }

    @Override
    public Result run(String s, TransactionConfig transactionConfig) {
        return null;
    }

    @Override
    public Result run(String s, Map<String, Object> map, TransactionConfig transactionConfig) {
        return null;
    }

    @Override
    public Result run(Query query, TransactionConfig transactionConfig) {
        return null;
    }

    @Override
    public Bookmark lastBookmark() {
        return null;
    }

    @Override
    public Set<Bookmark> lastBookmarks() {
        return Set.of();
    }
}