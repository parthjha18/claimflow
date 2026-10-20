package com.claimflow.demo;

import com.claimflow.auth.AuthService;
import com.claimflow.auth.RegisterOrgRequest;
import com.claimflow.claim.ClaimService;
import com.claimflow.claim.ClaimWorkflowService;
import com.claimflow.claim.domain.ExpenseCategory;
import com.claimflow.claim.dto.ClaimRequest;
import com.claimflow.claim.dto.LineItemRequest;
import com.claimflow.organization.OrganizationRepository;
import com.claimflow.security.UserPrincipal;
import com.claimflow.user.CreateUserRequest;
import com.claimflow.user.Role;
import com.claimflow.user.User;
import com.claimflow.user.UserRepository;
import com.claimflow.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Loads a demo organisation, one or more users per role and claims in every workflow state. Active only with
 * the {@code demo} profile (docker-compose enables it) and idempotent: it does nothing if the org exists.
 * It goes through the real services, so history, audit rows and notifications are exactly what the API makes.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    public static final String ORG_NAME = "Acme Demo Pvt Ltd";
    public static final String PASSWORD = "Demo@1234";

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final AuthService auth;
    private final UserService userService;
    private final ClaimService claimService;
    private final ClaimWorkflowService workflow;

    public DemoDataSeeder(OrganizationRepository organizations, UserRepository users, AuthService auth,
                          UserService userService, ClaimService claimService, ClaimWorkflowService workflow) {
        this.organizations = organizations;
        this.users = users;
        this.auth = auth;
        this.userService = userService;
        this.claimService = claimService;
        this.workflow = workflow;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (organizations.existsByNameIgnoreCase(ORG_NAME)) {
            log.info("Demo data already present, skipping");
            return;
        }
        auth.registerOrganization(new RegisterOrgRequest(ORG_NAME, "Asha Admin", "admin@acme.demo", PASSWORD));
        User admin = users.findByEmailIgnoreCase("admin@acme.demo").orElseThrow();

        User priya = as(admin, () -> userService.create(new CreateUserRequest(
                "priya.director@acme.demo", PASSWORD, "Priya Director", Role.MANAGER, null)));
        User rahul = as(admin, () -> userService.create(new CreateUserRequest(
                "rahul.manager@acme.demo", PASSWORD, "Rahul Manager", Role.MANAGER, priya.getId())));
        User anita = as(admin, () -> userService.create(new CreateUserRequest(
                "anita.employee@acme.demo", PASSWORD, "Anita Employee", Role.EMPLOYEE, rahul.getId())));
        User vikram = as(admin, () -> userService.create(new CreateUserRequest(
                "vikram.employee@acme.demo", PASSWORD, "Vikram Employee", Role.EMPLOYEE, rahul.getId())));
        User meera = as(admin, () -> userService.create(new CreateUserRequest(
                "meera.finance@acme.demo", PASSWORD, "Meera Finance", Role.FINANCE, null)));
        as(admin, () -> userService.create(new CreateUserRequest(
                "karan.finance@acme.demo", PASSWORD, "Karan Finance", Role.FINANCE, null)));

        // --- history: paid claims spread over the last four months, for the spend report -------------------
        for (int monthsAgo = 4; monthsAgo >= 1; monthsAgo--) {
            LocalDate day = LocalDate.now().minusMonths(monthsAgo).withDayOfMonth(10);
            paid(anita, rahul, meera, "Client visit, month -" + monthsAgo,
                    item(ExpenseCategory.TRAVEL, "4200.00", day, "Flight", true),
                    item(ExpenseCategory.FOOD, "850.00", day, "Team dinner", true));
            paid(vikram, rahul, meera, "Office supplies, month -" + monthsAgo,
                    item(ExpenseCategory.OTHER, "1500.00", day, "Stationery and cables", true));
        }
        LocalDate lastWeek = LocalDate.now().minusDays(7);
        // large claim: manager, then finance approval, then payment
        UUID bigPaid = create(anita, "Conference trip, Bengaluru", item(ExpenseCategory.TRAVEL, "18000.00", lastWeek, "Flights", true),
                item(ExpenseCategory.LODGING, "14000.00", lastWeek, "Hotel, 3 nights", true));
        as(anita, () -> workflow.submit(bigPaid));
        as(rahul, () -> workflow.approve(bigPaid, "Approved, valid event"));
        as(meera, () -> workflow.approve(bigPaid, "Within budget"));
        as(meera, () -> workflow.markPaid(bigPaid, "Paid via NEFT"));

        // --- one claim in every other state ------------------------------------------------------------------
        create(anita, "Draft: taxi receipts", item(ExpenseCategory.TRAVEL, "320.00", LocalDate.now().minusDays(2), "Airport taxi", false));

        UUID waiting = create(vikram, "Waiting for manager", item(ExpenseCategory.FOOD, "450.00", LocalDate.now().minusDays(1), "Client lunch", false));
        as(vikram, () -> workflow.submit(waiting));

        UUID waitingLarge = create(anita, "Waiting for manager (large)", item(ExpenseCategory.LODGING, "22000.00", lastWeek, "Offsite hotel", true));
        as(anita, () -> workflow.submit(waitingLarge));

        UUID readyToPay = create(vikram, "Approved, waiting for payment (small)", item(ExpenseCategory.FOOD, "400.00", lastWeek, "Working lunch", false));
        as(vikram, () -> workflow.submit(readyToPay));
        as(rahul, () -> workflow.approve(readyToPay, "OK"));

        UUID needsFinance = create(anita, "Manager approved, needs finance (large)", item(ExpenseCategory.TRAVEL, "26000.00", lastWeek, "Roadshow travel", true));
        as(anita, () -> workflow.submit(needsFinance));
        as(rahul, () -> workflow.approve(needsFinance, "Approved"));

        UUID financeApproved = create(vikram, "Finance approved, waiting for payment", item(ExpenseCategory.LODGING, "15000.00", lastWeek, "Training hotel", true));
        as(vikram, () -> workflow.submit(financeApproved));
        as(rahul, () -> workflow.approve(financeApproved, "Approved"));
        as(meera, () -> workflow.approve(financeApproved, "Approved"));

        UUID rejected = create(vikram, "Rejected: personal purchase", item(ExpenseCategory.OTHER, "2500.00", lastWeek, "Headphones", true));
        as(vikram, () -> workflow.submit(rejected));
        as(rahul, () -> workflow.reject(rejected, "Not a business expense"));

        UUID changes = create(anita, "Changes requested: missing detail", item(ExpenseCategory.FOOD, "700.00", lastWeek, "Dinner", true));
        as(anita, () -> workflow.submit(changes));
        as(rahul, () -> workflow.requestChanges(changes, "Please list attendees in the description"));

        log.info("Demo data loaded. Log in as admin@acme.demo / rahul.manager@acme.demo / anita.employee@acme.demo / "
                + "meera.finance@acme.demo with password {}", PASSWORD);
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private void paid(User owner, User manager, User finance, String title, SeedItem... items) {
        UUID id = create(owner, title, items);
        as(owner, () -> workflow.submit(id));
        as(manager, () -> workflow.approve(id, "Approved"));
        as(finance, () -> workflow.markPaid(id, "Paid"));
    }

    /** A line item to seed; {@code receipt} attaches a small demo PDF. */
    private record SeedItem(ExpenseCategory category, String amount, LocalDate date, String description, boolean receipt) {
    }

    private static SeedItem item(ExpenseCategory category, String amount, LocalDate date, String description, boolean receipt) {
        return new SeedItem(category, amount, date, description, receipt);
    }

    private static SeedItem item(ExpenseCategory category, String amount, LocalDate date, String description) {
        return new SeedItem(category, amount, date, description, false);
    }

    private UUID create(User owner, String title, SeedItem... seed) {
        List<LineItemRequest> items = java.util.Arrays.stream(seed)
                .map(i -> new LineItemRequest(i.category(), new BigDecimal(i.amount()), i.date(), i.description()))
                .toList();
        return as(owner, () -> {
            var detail = claimService.create(new ClaimRequest(title, "Demo data", items));
            UUID claimId = detail.claim().getId();
            for (int n = 0; n < seed.length; n++) {
                if (seed[n].receipt()) {
                    UUID itemId = detail.claim().getItems().get(n).getId();
                    claimService.attachReceipt(claimId, itemId, new SimplePdf("receipt-" + (n + 1) + ".pdf"));
                }
            }
            return claimId;
        });
    }

    private <T> T as(User user, java.util.function.Supplier<T> action) {
        var previous = SecurityContextHolder.getContext().getAuthentication();
        var principal = new UserPrincipal(user.getId(), user.getOrganizationId(), user.getEmail(), user.getRole());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private void as(User user, Runnable action) {
        as(user, () -> {
            action.run();
            return null;
        });
    }

    /** A tiny in-memory PDF-looking upload, so seeded receipts can be downloaded in the demo. */
    private static final class SimplePdf implements MultipartFile {
        private final String name;
        private final byte[] bytes = "%PDF-1.4\n% ClaimFlow demo receipt\n%%EOF\n".getBytes(StandardCharsets.UTF_8);

        SimplePdf(String name) {
            this.name = name;
        }

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return "application/pdf"; }
        @Override public boolean isEmpty() { return false; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public void transferTo(File dest) throws IOException { java.nio.file.Files.write(dest.toPath(), bytes); }
        @Override public void transferTo(Path dest) throws IOException { java.nio.file.Files.write(dest, bytes); }
    }
}
