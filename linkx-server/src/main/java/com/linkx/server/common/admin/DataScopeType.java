package com.linkx.server.common.admin;


/**
 * 作者：yangleduo
 */
/**
 * 角色数据权限范围。
 * <ul>
 *   <li>{@link #ALL}：不限制</li>
 *   <li>{@link #SELF}：仅本人</li>
 *   <li>{@link #DEPT}：本部门及下级部门用户</li>
 *   <li>{@link #CUSTOM}：角色配置的自定义组织（含下级）</li>
 * </ul>
 */
public final class DataScopeType {

    private DataScopeType() {
    }

    public static final int ALL = 1;
    public static final int SELF = 2;
    public static final int DEPT = 3;
    public static final int CUSTOM = 4;

    public static boolean isValid(Integer value) {
        return value != null
                && (value == ALL || value == SELF || value == DEPT || value == CUSTOM);
    }

    /** 取更宽的范围（ALL &gt; DEPT/CUSTOM &gt; SELF）。多角色实际过滤取用户集合并集。 */
    public static int widest(int a, int b) {
        if (a == ALL || b == ALL) {
            return ALL;
        }
        if (a == DEPT || b == DEPT || a == CUSTOM || b == CUSTOM) {
            if (a == DEPT || b == DEPT) {
                return DEPT;
            }
            return CUSTOM;
        }
        return SELF;
    }
}
