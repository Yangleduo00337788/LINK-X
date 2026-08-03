package com.linkx.server.service.admin.impl;

import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.common.admin.AdminConstants;
import com.linkx.server.common.admin.PageResultVO;
import com.linkx.server.controller.admin.dto.AdminAbnormalAccessQueryDTO;
import com.linkx.server.controller.admin.vo.AdminAbnormalAccessSummaryVO;
import com.linkx.server.controller.admin.vo.AdminAbnormalAccessVO;
import com.linkx.server.controller.admin.vo.AdminRateLimitHitVO;
import com.linkx.server.entity.SysLoginAudit;
import com.linkx.server.entity.admin.SysRiskEvent;
import com.linkx.server.mapper.SysLoginAuditMapper;
import com.linkx.server.mapper.admin.SysRiskEventMapper;
import com.linkx.server.service.IpGeoService;
import com.linkx.server.service.RateLimitService;
import com.linkx.server.service.admin.AdminAbnormalAccessService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAbnormalAccessServiceImpl implements AdminAbnormalAccessService {

    private static final String SOURCE_LOGIN_FAIL = "login_fail";
    private static final String SOURCE_RATE_LIMIT = "rate_limit";
    private static final String SOURCE_RISK_EVENT = "risk_event";
    private static final String SOURCE_ALL = "all";

    private static final Set<String> ACCESS_RISK_TYPES = Set.of(
            SysRiskEvent.TYPE_LOGIN_LOCK,
            SysRiskEvent.TYPE_RATE_LIMIT
    );

    private final SysLoginAuditMapper sysLoginAuditMapper;
    private final SysRiskEventMapper riskEventMapper;
    private final RateLimitService rateLimitService;
    private final IpGeoService ipGeoService;

    @Override
    public PageResultVO<AdminAbnormalAccessVO> list(AdminAbnormalAccessQueryDTO query) {
        String source = normalizeSource(query.getSource());
        return switch (source) {
            case SOURCE_LOGIN_FAIL -> listLoginFails(query);
            case SOURCE_RATE_LIMIT -> listRateLimitHits(query);
            case SOURCE_RISK_EVENT -> listRiskEvents(query);
            default -> listMerged(query);
        };
    }

    @Override
    public List<AdminAbnormalAccessVO> listForExport(AdminAbnormalAccessQueryDTO query) {
        AdminAbnormalAccessQueryDTO exportQuery = copyQuery(query);
        exportQuery.setPage(1);
        exportQuery.setSize(AdminConstants.EXPORT_MAX_SIZE);
        return list(exportQuery).getItems();
    }

    @Override
    public AdminAbnormalAccessSummaryVO summary() {
        Date since24h = hoursAgo(24);
        long loginFail24h = sysLoginAuditMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysLoginAudit::getSuccess).eq(0)
                        .and(SysLoginAudit::getCreateTime).ge(since24h));
        long rateLimitActive = rateLimitService.listActiveHits(null, 500).size();
        long riskPending = riskEventMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(SysRiskEvent::getStatus).eq(SysRiskEvent.STATUS_PENDING)
                        .and(SysRiskEvent::getEventType).in(ACCESS_RISK_TYPES));
        return AdminAbnormalAccessSummaryVO.builder()
                .loginFail24h(loginFail24h)
                .rateLimitActive(rateLimitActive)
                .riskEventPending(riskPending)
                .build();
    }

    private PageResultVO<AdminAbnormalAccessVO> listLoginFails(AdminAbnormalAccessQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = buildLoginFailQuery(query);
        long total = sysLoginAuditMapper.selectCountByQuery(qw);
        qw.orderBy(SysLoginAudit::getCreateTime, false);
        qw.limit((page - 1L) * size, size);
        List<AdminAbnormalAccessVO> items = sysLoginAuditMapper.selectListByQuery(qw).stream()
                .map(this::fromLoginFail)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    private PageResultVO<AdminAbnormalAccessVO> listRiskEvents(AdminAbnormalAccessQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        QueryWrapper qw = buildRiskEventQuery(query);
        long total = riskEventMapper.selectCountByQuery(qw);
        qw.orderBy(SysRiskEvent::getCreateTime, false);
        qw.limit((page - 1L) * size, size);
        List<AdminAbnormalAccessVO> items = riskEventMapper.selectListByQuery(qw).stream()
                .map(this::fromRiskEvent)
                .collect(Collectors.toList());
        return PageResultVO.of(items, page, size, total);
    }

    private PageResultVO<AdminAbnormalAccessVO> listRateLimitHits(AdminAbnormalAccessQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        List<AdminAbnormalAccessVO> all = rateLimitService.listActiveHits(query.getIp(), 500).stream()
                .map(this::fromRateLimitHit)
                .filter(item -> matchesKeyword(query.getKeyword(), item))
                .sorted(Comparator.comparing(AdminAbnormalAccessVO::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
        long total = all.size();
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(all.size(), from + size);
        List<AdminAbnormalAccessVO> pageItems = from >= all.size() ? List.of() : all.subList(from, to);
        return PageResultVO.of(pageItems, page, size, total);
    }

    private PageResultVO<AdminAbnormalAccessVO> listMerged(AdminAbnormalAccessQueryDTO query) {
        int page = normalizePage(query.getPage());
        int size = normalizeSize(query.getSize());
        int fetchEach = Math.min(Math.max(size * page, size), 200);

        AdminAbnormalAccessQueryDTO slice = copyQuery(query);
        slice.setPage(1);
        slice.setSize(fetchEach);

        List<AdminAbnormalAccessVO> merged = new ArrayList<>();
        merged.addAll(listLoginFails(slice).getItems());
        merged.addAll(listRiskEvents(slice).getItems());
        merged.addAll(listRateLimitHits(slice).getItems());
        merged = merged.stream()
                .filter(item -> matchesKeyword(query.getKeyword(), item))
                .sorted(Comparator.comparing(AdminAbnormalAccessVO::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toCollection(ArrayList::new));

        long total = countLoginFails(query) + countRiskEvents(query) + countRateLimitHits(query);
        int from = Math.max(0, (page - 1) * size);
        int to = Math.min(merged.size(), from + size);
        List<AdminAbnormalAccessVO> pageItems = from >= merged.size() ? List.of() : merged.subList(from, to);
        return PageResultVO.of(pageItems, page, size, total);
    }

    private long countLoginFails(AdminAbnormalAccessQueryDTO query) {
        return sysLoginAuditMapper.selectCountByQuery(buildLoginFailQuery(query));
    }

    private long countRiskEvents(AdminAbnormalAccessQueryDTO query) {
        return riskEventMapper.selectCountByQuery(buildRiskEventQuery(query));
    }

    private long countRateLimitHits(AdminAbnormalAccessQueryDTO query) {
        return rateLimitService.listActiveHits(query.getIp(), 500).stream()
                .map(this::fromRateLimitHit)
                .filter(item -> matchesKeyword(query.getKeyword(), item))
                .count();
    }

    private QueryWrapper buildLoginFailQuery(AdminAbnormalAccessQueryDTO query) {
        QueryWrapper qw = QueryWrapper.create().where(SysLoginAudit::getSuccess).eq(0);
        applyCommonFilters(qw, query, true);
        if (StringUtils.hasText(query.getIp())) {
            qw.and(SysLoginAudit::getIp).like(query.getIp().trim());
        }
        return qw;
    }

    private QueryWrapper buildRiskEventQuery(AdminAbnormalAccessQueryDTO query) {
        QueryWrapper qw = QueryWrapper.create().where(SysRiskEvent::getEventType).in(ACCESS_RISK_TYPES);
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            qw.and((QueryWrapper w) -> {
                w.where(SysRiskEvent::getTitle).like(kw)
                        .or(SysRiskEvent::getDetail).like(kw)
                        .or(SysRiskEvent::getUsername).like(kw)
                        .or(SysRiskEvent::getIp).like(kw);
            });
        }
        if (StringUtils.hasText(query.getIp())) {
            qw.and(SysRiskEvent::getIp).like(query.getIp().trim());
        }
        if (query.getStartTime() != null) {
            qw.and(SysRiskEvent::getCreateTime).ge(new Date(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            qw.and(SysRiskEvent::getCreateTime).le(new Date(query.getEndTime()));
        }
        return qw;
    }

    private void applyCommonFilters(QueryWrapper qw, AdminAbnormalAccessQueryDTO query, boolean loginAudit) {
        if (StringUtils.hasText(query.getKeyword())) {
            String kw = query.getKeyword().trim();
            if (loginAudit) {
                qw.and((QueryWrapper w) -> {
                    w.where(SysLoginAudit::getUsername).like(kw)
                            .or(SysLoginAudit::getIp).like(kw)
                            .or(SysLoginAudit::getReason).like(kw);
                });
            }
        }
        if (query.getStartTime() != null) {
            if (loginAudit) {
                qw.and(SysLoginAudit::getCreateTime).ge(new Date(query.getStartTime()));
            }
        }
        if (query.getEndTime() != null) {
            if (loginAudit) {
                qw.and(SysLoginAudit::getCreateTime).le(new Date(query.getEndTime()));
            }
        }
    }

    private AdminAbnormalAccessVO fromLoginFail(SysLoginAudit log) {
        String ip = ClientIpResolver.normalizeToIpv4(log.getIp());
        return AdminAbnormalAccessVO.builder()
                .source(SOURCE_LOGIN_FAIL)
                .sourceId(String.valueOf(log.getId()))
                .category(SOURCE_LOGIN_FAIL)
                .title("登录失败")
                .detail(log.getReason())
                .ip(ip)
                .region(ipGeoService.resolve(ip))
                .username(log.getUsername())
                .occurredAt(log.getCreateTime())
                .build();
    }

    private AdminAbnormalAccessVO fromRiskEvent(SysRiskEvent event) {
        String ip = ClientIpResolver.normalizeToIpv4(event.getIp());
        return AdminAbnormalAccessVO.builder()
                .source(SOURCE_RISK_EVENT)
                .sourceId(String.valueOf(event.getId()))
                .category(event.getEventType())
                .title(event.getTitle())
                .detail(event.getDetail())
                .ip(ip)
                .region(ipGeoService.resolve(ip))
                .username(event.getUsername())
                .riskLevel(event.getRiskLevel())
                .status(event.getStatus())
                .occurredAt(event.getCreateTime())
                .build();
    }

    private AdminAbnormalAccessVO fromRateLimitHit(AdminRateLimitHitVO hit) {
        String ip = ClientIpResolver.normalizeToIpv4(hit.getIp());
        String scope = hit.getScope() == null ? "" : hit.getScope();
        return AdminAbnormalAccessVO.builder()
                .source(SOURCE_RATE_LIMIT)
                .sourceId(hit.getRedisKey())
                .category(scope)
                .title("限流命中: " + scope)
                .detail(hit.getIdentity())
                .ip(ip)
                .region(ipGeoService.resolve(ip))
                .identity(hit.getIdentity())
                .hitCount(hit.getCount())
                .ttlSeconds(hit.getTtlSeconds())
                .occurredAt(new Date())
                .build();
    }

    private static boolean matchesKeyword(String keyword, AdminAbnormalAccessVO item) {
        if (!StringUtils.hasText(keyword) || item == null) {
            return true;
        }
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(item.getTitle(), kw)
                || containsIgnoreCase(item.getDetail(), kw)
                || containsIgnoreCase(item.getUsername(), kw)
                || containsIgnoreCase(item.getIdentity(), kw)
                || containsIgnoreCase(item.getIp(), kw)
                || containsIgnoreCase(item.getCategory(), kw);
    }

    private static boolean containsIgnoreCase(String value, String kw) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(kw);
    }

    private static String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) {
            return SOURCE_ALL;
        }
        return source.trim().toLowerCase(Locale.ROOT);
    }

    private static AdminAbnormalAccessQueryDTO copyQuery(AdminAbnormalAccessQueryDTO query) {
        AdminAbnormalAccessQueryDTO copy = new AdminAbnormalAccessQueryDTO();
        copy.setPage(query.getPage());
        copy.setSize(query.getSize());
        copy.setKeyword(query.getKeyword());
        copy.setStartTime(query.getStartTime());
        copy.setEndTime(query.getEndTime());
        copy.setSource(query.getSource());
        copy.setIp(query.getIp());
        return copy;
    }

    private static Date hoursAgo(int hours) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR_OF_DAY, -hours);
        return cal.getTime();
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? AdminConstants.DEFAULT_PAGE : page;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size < 1) {
            return AdminConstants.DEFAULT_SIZE;
        }
        return Math.min(size, AdminConstants.MAX_SIZE);
    }
}
