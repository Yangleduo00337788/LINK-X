package com.linkx.server.entity.admin;


/**
 * 作者：yangleduo
 */
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("sys_app_version")
public class SysAppVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_ARCHIVED = "archived";

    public static final String PLATFORM_WINDOWS = "windows";
    public static final String PLATFORM_MACOS = "macos";
    public static final String PLATFORM_LINUX = "linux";

    public static final String FORMAT_EXE = "exe";
    public static final String FORMAT_MSI = "msi";
    public static final String FORMAT_DMG = "dmg";
    public static final String FORMAT_APPIMAGE = "appimage";
    public static final String FORMAT_DEB = "deb";
    public static final String FORMAT_RPM = "rpm";

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    private String version;
    private String channel;
    private String platform;
    private String packageFormat;
    private String releaseNotes;
    private String downloadUrl;
    private String packageSha256;
    private String packageFileName;
    private Long packageSize;
    private Boolean forceUpdate;
    private String minSupportedVersion;
    private String status;
    private Date publishedAt;
    private Long publishedBy;
    private Long createdBy;
    private Long updatedBy;
    private Date createTime;
    private Date updateTime;
    private Integer deleted;
}
