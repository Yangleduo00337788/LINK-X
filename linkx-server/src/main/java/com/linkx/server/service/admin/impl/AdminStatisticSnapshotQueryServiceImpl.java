package com.linkx.server.service.admin.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.admin.SysAdminStatisticSnapshot;
import com.linkx.server.mapper.admin.SysAdminStatisticSnapshotMapper;
import com.linkx.server.service.admin.AdminStatisticSnapshotQueryService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminStatisticSnapshotQueryServiceImpl implements AdminStatisticSnapshotQueryService {

    private static final String DOMAIN_STATISTIC = "statistic";

    private final SysAdminStatisticSnapshotMapper statisticSnapshotMapper;

    @Override
    public Map<LocalDate, Long> loadDailyMetrics(String metricKey, LocalDate start, LocalDate end) {
        if (!StringUtils.hasText(metricKey) || start == null || end == null || start.isAfter(end)) {
            return Map.of();
        }
        List<SysAdminStatisticSnapshot> rows = statisticSnapshotMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysAdminStatisticSnapshot::getMetricDomain).eq(DOMAIN_STATISTIC)
                        .and(SysAdminStatisticSnapshot::getMetricKey).eq(metricKey.trim())
                        .and(SysAdminStatisticSnapshot::getDimensionKey).eq("all")
                        .and(SysAdminStatisticSnapshot::getSnapshotDate).ge(Date.valueOf(start))
                        .and(SysAdminStatisticSnapshot::getSnapshotDate).le(Date.valueOf(end)));
        Map<LocalDate, Long> map = new LinkedHashMap<>();
        for (SysAdminStatisticSnapshot row : rows) {
            if (row.getSnapshotDate() == null) {
                continue;
            }
            map.put(row.getSnapshotDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(),
                    row.getMetricValue() == null ? 0L : row.getMetricValue());
        }
        return map;
    }

    private static final String DOMAIN_HEATMAP = "heatmap";

    @Override
    public long[][] loadHeatmap(String metricKey, LocalDate start, LocalDate end) {
        long[][] matrix = new long[7][24];
        if (!StringUtils.hasText(metricKey) || start == null || end == null || start.isAfter(end)) {
            return matrix;
        }
        List<SysAdminStatisticSnapshot> rows = statisticSnapshotMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysAdminStatisticSnapshot::getMetricDomain).eq(DOMAIN_HEATMAP)
                        .and(SysAdminStatisticSnapshot::getMetricKey).eq(metricKey.trim())
                        .and(SysAdminStatisticSnapshot::getSnapshotDate).ge(Date.valueOf(start))
                        .and(SysAdminStatisticSnapshot::getSnapshotDate).le(Date.valueOf(end)));
        for (SysAdminStatisticSnapshot row : rows) {
            int wd = parseBucketIndex(row.getDimensionKey());
            int h = parseBucketIndex(row.getDimensionValue());
            if (wd < 0 || wd >= 7 || h < 0 || h >= 24) {
                continue;
            }
            matrix[wd][h] += row.getMetricValue() == null ? 0L : row.getMetricValue();
        }
        return matrix;
    }

    private static int parseBucketIndex(String value) {
        if (!StringUtils.hasText(value)) {
            return -1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}
