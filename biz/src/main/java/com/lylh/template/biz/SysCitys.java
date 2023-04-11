package com.lylh.template.biz;

import lombok.Data;

@Data
public class SysCitys {

    private String areaCode; //区域代码
    private String areaName; //区域名称
    private String pAreaCode; //区域父级代码
    private Integer level; // 区域级别（1-5级）
    private String tcCode; //五级城乡分类代码
}
