/*
 * Copyright 2010-2011 Ning, Inc.
 *
 * Ning licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.ning.billing.invoice.dao;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.joda.time.DateTime;
import org.testng.annotations.Test;
import com.ning.billing.catalog.api.Currency;
import com.ning.billing.invoice.api.Invoice;
import com.ning.billing.invoice.api.InvoiceItem;
import com.ning.billing.invoice.api.InvoicePayment;
import com.ning.billing.invoice.model.DefaultInvoice;
import com.ning.billing.invoice.model.DefaultInvoiceItem;
import com.ning.billing.invoice.model.DefaultInvoicePayment;
import com.ning.billing.util.clock.DefaultClock;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

@Test(groups = {"invoicing", "invoicing-invoiceDao"})
public class InvoiceDaoTests extends InvoiceDaoTestBase {
    private final int NUMBER_OF_DAY_BETWEEN_RETRIES = 8;

    @Test
    public void testCreationAndRetrievalByAccount() {
        UUID accountId = UUID.randomUUID();
        Invoice invoice = new DefaultInvoice(accountId, new DefaultClock().getUTCNow(), Currency.USD);
        DateTime invoiceDate = invoice.getInvoiceDate();

        invoiceDao.create(invoice);

        List<Invoice> invoices = invoiceDao.getInvoicesByAccount(accountId);
        assertNotNull(invoices);
        assertEquals(invoices.size(), 1);
        Invoice thisInvoice = invoices.get(0);
        assertEquals(invoice.getAccountId(), accountId);
        assertTrue(thisInvoice.getInvoiceDate().compareTo(invoiceDate) == 0);
        assertEquals(thisInvoice.getCurrency(), Currency.USD);
        assertEquals(thisInvoice.getNumberOfItems(), 0);
        assertTrue(thisInvoice.getTotalAmount().compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    public void testInvoicePayment() {
        UUID accountId = UUID.randomUUID();
        Invoice invoice = new DefaultInvoice(accountId, new DefaultClock().getUTCNow(), Currency.USD);
        UUID invoiceId = invoice.getId();
        UUID subscriptionId = UUID.randomUUID();
        DateTime startDate = new DateTime(2010, 1, 1, 0, 0, 0, 0);
        DateTime endDate = new DateTime(2010, 4, 1, 0, 0, 0, 0);
        InvoiceItem invoiceItem = new DefaultInvoiceItem(invoiceId, subscriptionId, startDate, endDate, "test", new BigDecimal("21.00"), new BigDecimal("7.00"), Currency.USD);
        invoice.addInvoiceItem(invoiceItem);
        invoiceDao.create(invoice);

        Invoice savedInvoice = invoiceDao.getById(invoiceId);
        assertNotNull(savedInvoice);
        assertEquals(savedInvoice.getTotalAmount().compareTo(new BigDecimal("21.00")), 0);
        assertEquals(savedInvoice.getBalance().compareTo(new BigDecimal("21.00")), 0);
        assertEquals(savedInvoice.getAmountPaid(), BigDecimal.ZERO);
        assertEquals(savedInvoice.getInvoiceItems().size(), 1);

        BigDecimal paymentAmount = new BigDecimal("11.00");
        UUID paymentId = UUID.randomUUID();
        invoiceDao.notifySuccessfulPayment(invoiceId, paymentAmount, Currency.USD, paymentId, new DefaultClock().getUTCNow().plusDays(12));

        Invoice retrievedInvoice = invoiceDao.getById(invoiceId);
        assertNotNull(retrievedInvoice);
        assertEquals(retrievedInvoice.getInvoiceItems().size(), 1);
        assertEquals(retrievedInvoice.getTotalAmount().compareTo(new BigDecimal("21.00")), 0);
        assertEquals(retrievedInvoice.getBalance().compareTo(new BigDecimal("10.00")), 0);
        assertEquals(retrievedInvoice.getAmountPaid().compareTo(new BigDecimal("11.00")), 0);
    }

    @Test
    public void testRetrievalForNonExistentInvoiceId() {
        Invoice invoice = invoiceDao.getById(UUID.randomUUID());
        assertNull(invoice);
    }

    @Test
    public void testAddPayment() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice = new DefaultInvoice(accountId, targetDate, Currency.USD);

        UUID paymentId = UUID.randomUUID();
        DateTime paymentAttemptDate = new DateTime(2011, 6, 24, 12, 14, 36, 0);
        BigDecimal paymentAmount = new BigDecimal("14.0");

        invoiceDao.create(invoice);
        invoiceDao.notifySuccessfulPayment(invoice.getId(), paymentAmount, Currency.USD, paymentId, paymentAttemptDate);

        invoice = invoiceDao.getById(invoice.getId());
        assertEquals(invoice.getAmountPaid().compareTo(paymentAmount), 0);
        assertTrue(invoice.getLastPaymentAttempt().equals(paymentAttemptDate));
        assertEquals(invoice.getNumberOfPayments(), 1);
    }

    @Test
    public void testAddPaymentAttempt() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice = new DefaultInvoice(accountId, targetDate, Currency.USD);

        DateTime paymentAttemptDate = new DateTime(2011, 6, 24, 12, 14, 36, 0);

        invoiceDao.create(invoice);
        invoiceDao.notifyFailedPayment(invoice.getId(), UUID.randomUUID(), paymentAttemptDate);

        invoice = invoiceDao.getById(invoice.getId());
        assertTrue(invoice.getLastPaymentAttempt().equals(paymentAttemptDate));
    }

    @Test
    public void testGetInvoicesForPaymentWithNoResults() {
        DateTime notionalDate = new DateTime();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        
        // determine the number of existing invoices available for payment (to avoid side effects from other tests)
        List<UUID> invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        int existingInvoiceCount = invoices.size();
        
        UUID accountId = UUID.randomUUID();
        Invoice invoice = new DefaultInvoice(accountId, targetDate, Currency.USD);

        invoiceDao.create(invoice);
        invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        assertEquals(invoices.size(), existingInvoiceCount);
    }

    @Test
    public void testGetInvoicesForPayment() {
        List<UUID> invoices;
        DateTime notionalDate = new DateTime();

        // create a new invoice with one item
        UUID accountId = UUID.randomUUID();
        DateTime targetDate = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice = new DefaultInvoice(accountId, targetDate, Currency.USD);

        UUID invoiceId = invoice.getId();
        UUID subscriptionId = UUID.randomUUID();
        DateTime endDate = targetDate.plusMonths(3);
        BigDecimal rate = new BigDecimal("9.0");
        BigDecimal amount = rate.multiply(new BigDecimal("3.0"));

        DefaultInvoiceItem item = new DefaultInvoiceItem(invoiceId, subscriptionId, targetDate, endDate, "test", amount, rate, Currency.USD);
        invoice.addInvoiceItem(item);
        invoiceDao.create(invoice);

        // ensure that the number of invoices for payment has increased by 1
        int count;
        invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        List<Invoice> invoicesDue = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate);
        count = invoicesDue.size();
        assertEquals(invoices.size(), count);

        // attempt a payment; ensure that the number of invoices for payment has decreased by 1
        // (no retries for NUMBER_OF_DAYS_BETWEEN_RETRIES days)
        invoiceDao.notifyFailedPayment(invoice.getId(), UUID.randomUUID(), notionalDate);
        invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);

        // advance clock by NUMBER_OF_DAYS_BETWEEN_RETRIES days
        // ensure that number of invoices for payment has increased by 1 (retry)
        notionalDate = notionalDate.plusDays(NUMBER_OF_DAY_BETWEEN_RETRIES);
        invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);

        // post successful partial payment; ensure that number of invoices for payment has decreased by 1
        invoiceDao.notifySuccessfulPayment(invoiceId, new BigDecimal("22.0000"), Currency.USD, UUID.randomUUID(), notionalDate);
        invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);

        // get invoice; verify amount paid is correct
        invoice = invoiceDao.getById(invoiceId);
        assertEquals(invoice.getAmountPaid().compareTo(new BigDecimal("22.0")), 0);

        // advance clock NUMBER_OF_DAYS_BETWEEN_RETRIES days
        // ensure that number of invoices for payment has increased by 1 (retry)
        notionalDate = notionalDate.plusDays(NUMBER_OF_DAY_BETWEEN_RETRIES);
        invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);

        // post completed payment; ensure that the number of invoices for payment has decreased by 1
        invoiceDao.notifySuccessfulPayment(invoiceId, new BigDecimal("5.0000"), Currency.USD, UUID.randomUUID(), notionalDate);
        invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);

        // get invoice; verify amount paid is correct
        invoice = invoiceDao.getById(invoiceId);
        assertEquals(invoice.getAmountPaid().compareTo(new BigDecimal("27.0")), 0);

        // advance clock by NUMBER_OF_DAYS_BETWEEN_RETRIES days
        // ensure that the number of invoices for payment hasn't changed
        notionalDate = notionalDate.plusDays(NUMBER_OF_DAY_BETWEEN_RETRIES);
        invoices = invoiceDao.getInvoicesForPayment(notionalDate, NUMBER_OF_DAY_BETWEEN_RETRIES);
        count = getInvoicesDueForPaymentAttempt(invoiceDao.get(), notionalDate).size();
        assertEquals(invoices.size(), count);
    }

    private List<Invoice> getInvoicesDueForPaymentAttempt(final List<Invoice> invoices, final DateTime date) {
        List<Invoice> invoicesDue= new ArrayList<Invoice>();

        for (final Invoice invoice : invoices) {
            if (invoice.isDueForPayment(date, NUMBER_OF_DAY_BETWEEN_RETRIES)) {
                invoicesDue.add(invoice);
            }
        }

        return invoicesDue;
    }

    @Test
    public void testGetInvoicesBySubscription() {
        UUID accountId = UUID.randomUUID();

        UUID subscriptionId1 = UUID.randomUUID(); BigDecimal rate1 = new BigDecimal("17.0");
        UUID subscriptionId2 = UUID.randomUUID(); BigDecimal rate2 = new BigDecimal("42.0");
        UUID subscriptionId3 = UUID.randomUUID(); BigDecimal rate3 = new BigDecimal("3.0");
        UUID subscriptionId4 = UUID.randomUUID(); BigDecimal rate4 = new BigDecimal("12.0");

        DateTime targetDate = new DateTime(2011, 5, 23, 0, 0, 0, 0);


        // create invoice 1 (subscriptions 1-4)
        Invoice invoice1 = new DefaultInvoice(accountId, targetDate, Currency.USD);
        invoiceDao.create(invoice1);

        UUID invoiceId1 = invoice1.getId();

        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        DateTime endDate = startDate.plusMonths(1);

        DefaultInvoiceItem item1 = new DefaultInvoiceItem(invoiceId1, subscriptionId1, startDate, endDate, "test A", rate1, rate1, Currency.USD);
        invoiceItemDao.create(item1);

        DefaultInvoiceItem item2 = new DefaultInvoiceItem(invoiceId1, subscriptionId2, startDate, endDate, "test B", rate2, rate2, Currency.USD);
        invoiceItemDao.create(item2);

        DefaultInvoiceItem item3 = new DefaultInvoiceItem(invoiceId1, subscriptionId3, startDate, endDate, "test C", rate3, rate3, Currency.USD);
        invoiceItemDao.create(item3);

        DefaultInvoiceItem item4 = new DefaultInvoiceItem(invoiceId1, subscriptionId4, startDate, endDate, "test D", rate4, rate4, Currency.USD);
        invoiceItemDao.create(item4);

        // create invoice 2 (subscriptions 1-3)
        DefaultInvoice invoice2 = new DefaultInvoice(accountId, targetDate, Currency.USD);
        invoiceDao.create(invoice2);

        UUID invoiceId2 = invoice2.getId();

        startDate = endDate;
        endDate = startDate.plusMonths(1);

        DefaultInvoiceItem item5 = new DefaultInvoiceItem(invoiceId2, subscriptionId1, startDate, endDate, "test A", rate1, rate1, Currency.USD);
        invoiceItemDao.create(item5);

        DefaultInvoiceItem item6 = new DefaultInvoiceItem(invoiceId2, subscriptionId2, startDate, endDate, "test B", rate2, rate2, Currency.USD);
        invoiceItemDao.create(item6);

        DefaultInvoiceItem item7 = new DefaultInvoiceItem(invoiceId2, subscriptionId3, startDate, endDate, "test C", rate3, rate3, Currency.USD);
        invoiceItemDao.create(item7);

        // check that each subscription returns the correct number of invoices
        List<Invoice> items1 = invoiceDao.getInvoicesBySubscription(subscriptionId1);
        assertEquals(items1.size(), 2);

        List<Invoice> items2 = invoiceDao.getInvoicesBySubscription(subscriptionId2);
        assertEquals(items2.size(), 2);

        List<Invoice> items3 = invoiceDao.getInvoicesBySubscription(subscriptionId3);
        assertEquals(items3.size(), 2);

        List<Invoice> items4 = invoiceDao.getInvoicesBySubscription(subscriptionId4);
        assertEquals(items4.size(), 1);
    }

    @Test
    public void testGetInvoicesForAccountAfterDate() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate1 = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice1 = new DefaultInvoice(accountId, targetDate1, Currency.USD);
        invoiceDao.create(invoice1);

        DateTime targetDate2 = new DateTime(2011, 12, 6, 0, 0, 0, 0);
        Invoice invoice2 = new DefaultInvoice(accountId, targetDate2, Currency.USD);
        invoiceDao.create(invoice2);


        List<Invoice> invoices;
        invoices = invoiceDao.getInvoicesByAccount(accountId, new DateTime(2011, 1, 1, 0, 0, 0, 0));
        assertEquals(invoices.size(), 2);

        invoices = invoiceDao.getInvoicesByAccount(accountId, new DateTime(2011, 10, 6, 0, 0, 0, 0));
        assertEquals(invoices.size(), 2);

        invoices = invoiceDao.getInvoicesByAccount(accountId, new DateTime(2011, 10, 11, 0, 0, 0, 0));
        assertEquals(invoices.size(), 1);

        invoices = invoiceDao.getInvoicesByAccount(accountId, new DateTime(2011, 12, 6, 0, 0, 0, 0));
        assertEquals(invoices.size(), 1);

        invoices = invoiceDao.getInvoicesByAccount(accountId, new DateTime(2012, 1, 1, 0, 0, 0, 0));
        assertEquals(invoices.size(), 0);
    }

    @Test
    public void testAccountBalance() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate1 = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice1 = new DefaultInvoice(accountId, targetDate1, Currency.USD);
        invoiceDao.create(invoice1);

        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        DateTime endDate = startDate.plusMonths(1);

        BigDecimal rate1 = new BigDecimal("17.0");
        BigDecimal rate2 = new BigDecimal("42.0");

        DefaultInvoiceItem item1 = new DefaultInvoiceItem(invoice1.getId(), UUID.randomUUID(), startDate, endDate, "test A", rate1, rate1, Currency.USD);
        invoiceItemDao.create(item1);

        DefaultInvoiceItem item2 = new DefaultInvoiceItem(invoice1.getId(), UUID.randomUUID(), startDate, endDate, "test B", rate2, rate2, Currency.USD);
        invoiceItemDao.create(item2);

        BigDecimal payment1 = new BigDecimal("48.0");
        InvoicePayment payment = new DefaultInvoicePayment(invoice1.getId(), new DateTime(), payment1, Currency.USD);
        invoicePaymentDao.create(payment);

        BigDecimal balance = invoiceDao.getAccountBalance(accountId);
        assertEquals(balance.compareTo(rate1.add(rate2).subtract(payment1)), 0);
    }

    @Test
    public void testAccountBalanceWithNoPayments() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate1 = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice1 = new DefaultInvoice(accountId, targetDate1, Currency.USD);
        invoiceDao.create(invoice1);

        DateTime startDate = new DateTime(2011, 3, 1, 0, 0, 0, 0);
        DateTime endDate = startDate.plusMonths(1);

        BigDecimal rate1 = new BigDecimal("17.0");
        BigDecimal rate2 = new BigDecimal("42.0");

        DefaultInvoiceItem item1 = new DefaultInvoiceItem(invoice1.getId(), UUID.randomUUID(), startDate, endDate, "test A", rate1, rate1, Currency.USD);
        invoiceItemDao.create(item1);

        DefaultInvoiceItem item2 = new DefaultInvoiceItem(invoice1.getId(), UUID.randomUUID(), startDate, endDate, "test B", rate2, rate2, Currency.USD);
        invoiceItemDao.create(item2);

        BigDecimal balance = invoiceDao.getAccountBalance(accountId);
        assertEquals(balance.compareTo(rate1.add(rate2)), 0);
    }


    @Test
    public void testAccountBalanceWithNoInvoiceItems() {
        UUID accountId = UUID.randomUUID();
        DateTime targetDate1 = new DateTime(2011, 10, 6, 0, 0, 0, 0);
        Invoice invoice1 = new DefaultInvoice(accountId, targetDate1, Currency.USD);
        invoiceDao.create(invoice1);

        BigDecimal payment1 = new BigDecimal("48.0");
        InvoicePayment payment = new DefaultInvoicePayment(invoice1.getId(), new DateTime(), payment1, Currency.USD);
        invoicePaymentDao.create(payment);

        BigDecimal balance = invoiceDao.getAccountBalance(accountId);
        assertEquals(balance.compareTo(BigDecimal.ZERO.subtract(payment1)), 0);
    }
}
