package io.github.miklires.mauction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuctionRepositoryTest {
    private AuctionRepository repo;

    @BeforeEach void open() {
        repo = new AuctionRepository("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        repo.initialize().join();
    }

    @AfterEach void close() { repo.close(); }

    @Test void onlyOneBuyerCanReserve() {
        UUID seller = UUID.randomUUID();
        Listing listing = create(seller, 10);
        assertEquals(PurchaseResult.Status.RESERVED, repo.reserve(listing.id(), UUID.randomUUID(), "A").join().status());
        assertEquals(PurchaseResult.Status.NOT_ACTIVE, repo.reserve(listing.id(), UUID.randomUUID(), "B").join().status());
    }

    @Test void ownerCannotBuyOwnListing() {
        UUID seller = UUID.randomUUID();
        assertEquals(PurchaseResult.Status.OWN_LISTING, repo.reserve(create(seller, 10).id(), seller, "Seller").join().status());
    }

    @Test void expiredAndCancelledItemsRemainReturnable() {
        UUID seller = UUID.randomUUID();
        Listing expired = repo.create(seller,"Seller",new byte[]{1},10,Instant.now().minusSeconds(1)).join();
        Listing cancelled = create(seller, 10);
        assertTrue(repo.cancel(cancelled.id(),seller,false).join().isPresent());
        assertEquals(Set.of(expired.id(),cancelled.id()),repo.returns(seller).join().stream().map(Listing::id).collect(java.util.stream.Collectors.toSet()));
        assertTrue(repo.returned(expired.id(),seller).join());
        assertEquals(List.of(cancelled.id()),repo.returns(seller).join().stream().map(Listing::id).toList());
    }

    @Test void committedItemStaysPendingUntilClaimedAndDelivered() {
        UUID seller=UUID.randomUUID(), buyer=UUID.randomUUID();
        Listing listing=create(seller,10);
        PurchaseResult purchase=repo.reserve(listing.id(),buyer,"Buyer").join();
        assertTrue(repo.beginWithdrawal(purchase.transactionId()).join());
        assertTrue(repo.moneyWithdrawn(purchase.transactionId()).join());
        repo.commitSale(purchase.transactionId(),listing.id(),9.5).join();
        assertEquals(1,repo.pending(buyer).join().size());
        assertTrue(repo.claimDelivery(purchase.transactionId()).join());
        assertFalse(repo.claimDelivery(purchase.transactionId()).join());
        repo.completeDelivery(purchase.transactionId()).join();
        assertTrue(repo.pending(buyer).join().isEmpty());
        assertEquals(1,repo.payouts(seller).join().size());
        assertTrue(repo.claimPayout(purchase.transactionId()).join());
        repo.completePayout(purchase.transactionId()).join();
        assertTrue(repo.payouts(seller).join().isEmpty());
    }

    @Test void failedPaymentReleasesReservedListing() {
        UUID seller=UUID.randomUUID(), buyer=UUID.randomUUID();
        Listing listing=create(seller,10);
        PurchaseResult purchase=repo.reserve(listing.id(),buyer,"Buyer").join();
        assertTrue(repo.beginWithdrawal(purchase.transactionId()).join());
        repo.release(purchase.transactionId(),listing.id()).join();
        assertEquals(PurchaseResult.Status.RESERVED,repo.reserve(listing.id(),UUID.randomUUID(),"Next").join().status());
    }

    @Test void maintenanceReleasesOnlyPrePaymentReservations() {
        UUID seller=UUID.randomUUID();
        Listing stale=create(seller,10), uncertain=create(seller,20);
        PurchaseResult first=repo.reserve(stale.id(),UUID.randomUUID(),"First").join();
        PurchaseResult second=repo.reserve(uncertain.id(),UUID.randomUUID(),"Second").join();
        assertTrue(repo.beginWithdrawal(second.transactionId()).join());
        assertEquals(1,repo.maintenance(Instant.now().plusSeconds(1),Instant.EPOCH).join());
        assertEquals(PurchaseResult.Status.RESERVED,repo.reserve(stale.id(),UUID.randomUUID(),"Next").join().status());
        assertEquals(PurchaseResult.Status.NOT_ACTIVE,repo.reserve(uncertain.id(),UUID.randomUUID(),"Blocked").join().status());
        assertNotNull(first.transactionId());
    }

    @Test void limitIsEnforcedInsideCreate() {
        UUID seller=UUID.randomUUID();
        assertTrue(repo.createLimited(seller,"Seller",new byte[]{1},1,Instant.now().plusSeconds(60),"stone",1).join().isPresent());
        assertTrue(repo.createLimited(seller,"Seller",new byte[]{2},2,Instant.now().plusSeconds(60),"dirt",1).join().isEmpty());
    }

    @Test void browseSearchesSortsAndPages() {
        UUID seller=UUID.randomUUID();
        repo.createLimited(seller,"Alice",new byte[]{1},20,Instant.now().plusSeconds(60),"diamond sword",10).join();
        repo.createLimited(seller,"Alice",new byte[]{2},10,Instant.now().plusSeconds(60),"diamond pickaxe",10).join();
        var page=repo.browse("diamond",null,AuctionRepository.SortOrder.PRICE_LOW,0,9).join();
        assertEquals(2,page.total());
        assertEquals(10,page.listings().getFirst().price());
        assertEquals(1,page.pages());
    }

    private Listing create(UUID seller,double price) {
        return repo.create(seller,"Seller",new byte[]{1,2},price,Instant.now().plusSeconds(60)).join();
    }
}
