package com.fraudsense.event;

import com.fraudsense.model.Transaction;

/** INTERFACE: implemented by dashboards to receive every processed transaction. */
public interface TransactionListener {
    void onTransactionProcessed(Transaction t);
}