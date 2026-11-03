package net.likelion.bebc25.quoteon.admin;

import net.likelion.bebc25.quoteon.admin.dto.AdminDashboardResponse;
import net.likelion.bebc25.quoteon.admin.dto.PopularProductResponse;
import net.likelion.bebc25.quoteon.member.MemberStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminDashboardService {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int POPULAR_PRODUCT_LIMIT = 5;

    private final AdminDashboardRepository adminDashboardRepository;

    public AdminDashboardService(AdminDashboardRepository adminDashboardRepository) {
        this.adminDashboardRepository = adminDashboardRepository;
    }

    public AdminDashboardResponse getDashboard() {
        LocalDate today = LocalDate.now(SEOUL);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime nextMonthStart = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();
        List<AdminDashboardRepository.ProductQuoteCount> productCounts =
                adminDashboardRepository.countQuotesByProductCode(monthStart, nextMonthStart);
        return new AdminDashboardResponse(
                adminDashboardRepository.countQuotes(todayStart, tomorrowStart),
                adminDashboardRepository.sumQuoteTotalAmount(monthStart, nextMonthStart),
                adminDashboardRepository.countMembersByStatus(MemberStatus.PENDING),
                adminDashboardRepository.countActiveSalesMembers(),
                popularProducts(productCounts),
                adminDashboardRepository.findMemberQuoteRanks(monthStart, nextMonthStart)
        );
    }

    private static List<PopularProductResponse> popularProducts(
            List<AdminDashboardRepository.ProductQuoteCount> productCounts
    ) {
        long total = 0L;
        for (AdminDashboardRepository.ProductQuoteCount product : productCounts) {
            total += product.quoteCount();
        }
        int limit = Math.min(POPULAR_PRODUCT_LIMIT, productCounts.size());
        List<PopularProductResponse> products = new ArrayList<>();
        for (int index = 0; index < limit; index++) {
            AdminDashboardRepository.ProductQuoteCount product = productCounts.get(index);
            products.add(new PopularProductResponse(
                    product.productCode(),
                    product.quoteCount(),
                    ratio(product.quoteCount(), total)
            ));
        }
        return List.copyOf(products);
    }

    private static BigDecimal ratio(long quoteCount, long total) {
        if (total == 0L) {
            return BigDecimal.ZERO.setScale(1);
        }
        return BigDecimal.valueOf(quoteCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }
}
