package com.lylh.template.biz;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

/**
 * Jsoup抓取页面数据，五级行政区域划分，并保存到数据库
 *
 * tips：1.过滤各级市辖区（市辖区编码统一为父级市编码 + “01”）;
 * tips：2.编码并未做补0处理，五级编码为12位（2+2+2+3+3），可按需补0;
 * tips：3.有生僻字，表格式见V1_init.sql，CREATE TABLE `area`，注意数据库格式（mysql utf8mb4）;
 */
public class TestJsoup {

    //统计局2022年五级行政区域划分数据来源
    private static final String url = "http://www.stats.gov.cn/tjsj/tjbz/tjyqhdmhcxhfdm/2022/";
    //重试次数，重试以解决偶尔获取不到页面的问题
    private static int retryTimes = 100;

    public static void main(String[] args) {
        getProvinces();
    }

    /**
     * 保存数据到数据库
     */
    public static void saveDB(SysCitys city) {
        try {
            String URL="jdbc:mysql://localhost:3306/test?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC";
            String USER="root";
            String PASSWORD="root";
            //1.加载驱动程序
            Class.forName("com.mysql.cj.jdbc.Driver");
            //2.获得数据库链接
            Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
            //3.通过数据库的连接操作数据库，实现增删改查（使用Statement类）
            String s = "insert into area(area_code,area_name,p_area_code, level, tc_code) values(?,?,?,?,?)";
            PreparedStatement pst = conn.prepareStatement(s);

            pst.setString(1, city.getAreaCode());
            pst.setString(2, city.getAreaName());
            pst.setString(3, city.getPAreaCode());
            pst.setInt(4, city.getLevel());
            pst.setString(5, city.getTcCode());

            pst.execute();
            //关闭资源
            pst.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取所有的省份
     * @return
     */
    public static List<SysCitys> getProvinces() {
        List<SysCitys> sysAreas = new ArrayList<>();
        Document connect = connect(url+"index.html");
        Elements rowProvince = connect.select("tr.provincetr");
        for (Element provinceElement : rowProvince) {
            Elements select = provinceElement.select("a");
            for (Element province : select) {
                String codUrl = province.select("a").attr("href");
                String fatherCode = codUrl.replace(".html", "");
                String name = province.text();

                //测试只拿江西省的
//               if ("江西省".equals(name)) {
                   SysCitys sysCitys = returnCitys(fatherCode, name, null, 1, null);
                   saveDB(sysCitys);
                   sysAreas.add(sysCitys);
                   System.err.println("++++++++++++++++++++++++++开始获取" + name + "下属市区行政区划信息++++++++++++++++++++++++");
                   String provinceUrl = url + codUrl;
                   List<SysCitys> sysAreasList = getCitys(provinceUrl, fatherCode);
//                   sysAreas.addAll(sysAreasList);
//               }
            }
        }
        return sysAreas;
    }


    /**
     * 获取市行政区划信息
     * @param provinceUrl 省份对应的地址
     * @param parentCode  需要爬取的省份行政区划（对于市的父级代码即为省行政区划）
     * @return
     */
    public static List<SysCitys> getCitys(String provinceUrl, String parentCode){
        List<SysCitys> sysAreas = new ArrayList<>();
        Document connect = connect(provinceUrl);
        Elements rowCity = connect.select("tr.citytr");
        for (Element cityElement : rowCity) {
            String codUrl = cityElement.select("a").attr("href");
            String name = cityElement.select("td").text();
            String[] split = name.split(" ");
            //测试只拿抚州市
//            if ("抚州市".equals(split[1])) {
            if (!"市辖区".equals(split[1])) {
                String addrCode = split[0].substring(0, 4);
                SysCitys sysCitys = returnCitys(addrCode, split[1], parentCode, 2, null);
                saveDB(sysCitys);
                sysAreas.add(sysCitys);
                System.err.println("-------------------开始获取" + split[1] + "下属区县行政区划信息-----------------------");
                String cityUrl = url + codUrl;
                List<SysCitys> downAreaCodeList = getCountys(cityUrl, addrCode);
//                sysAreas.addAll(downAreaCodeList);
            }
//            }
        }
        return sysAreas;
    }

    /**
     * 获取区县行政区划信息
     * @param cityUrl 城市对应的地址
     * @param parentCode  需要爬取的市行政区划（对于区县的父级代码即为市行政区划）
     * @return
     */
    public static List<SysCitys> getCountys(String cityUrl, String parentCode){
        List<SysCitys> sysAreas = new ArrayList<>();
        Document connect = connect(cityUrl);
        Elements rowDown = connect.select("tr.countytr");
        for (Element downElement : rowDown) {
            String codUrl = downElement.select("a").attr("href");
            String name = downElement.select("td").text();
            String[] split = name.split(" ");
            if (!"市辖区".equals(split[1])) {
                String addrCode = split[0].substring(0, 6);
                SysCitys sysCitys = returnCitys(addrCode, split[1], parentCode, 3, null);
                saveDB(sysCitys);
                sysAreas.add(sysCitys);
                //市辖区下不再有分级，因此没有下级地址的，不再去抓取数据
                if (StringUtils.isNotBlank(codUrl)) {
                    System.err.println("-------------------开始获取" + split[1] + "下属镇乡行政区划信息-----------------------");
                    String townUrl = url + addrCode.substring(0, 2) + "/" + codUrl;
                    List<SysCitys> downAreaCodeList = getTowns(townUrl, addrCode);
//                sysAreas.addAll(downAreaCodeList);
                }
            }
        }
        return sysAreas;
    }

    /**
     * 获取乡镇行政区划信息
     * @param townUrl
     * @param parentCode
     * @return
     */
    private static List<SysCitys> getTowns(String townUrl, String parentCode) {
        List<SysCitys> sysAreas = new ArrayList<>();
        Document connect = connect(townUrl);
        Elements rowDown = connect.select("tr.towntr");
        for (Element downElement : rowDown) {
            String codUrl = downElement.select("a").attr("href");
            String name = downElement.select("td").text();
            String[] split = name.split(" ");
            String addrCode = split[0].substring(0,9);
            SysCitys sysCitys = returnCitys(addrCode,split[1],parentCode,4,null);
            saveDB(sysCitys);
            sysAreas.add(sysCitys);
            System.err.println("-------------------开始获取"+split[1]+"下属村街道行政区划信息-----------------------");
            String villageUrl =  url+ addrCode.substring(0,2) + "/" + addrCode.substring(2,4) + "/" + codUrl;
            List<SysCitys> downAreaCodeList = getVillages(villageUrl, addrCode);
//            sysAreas.addAll(downAreaCodeList);xx
        }
        return sysAreas;
    }

    /**
     * 获取村街道行政区划信息
     * @param villageUrl
     * @param parentCode
     * @return
     */
    private static List<SysCitys> getVillages(String villageUrl, String parentCode) {
        List<SysCitys> sysAreas = new ArrayList<>();
        Document connect = connect(villageUrl);
        Elements rowDown = connect.select("tr.villagetr");
        for (Element downElement : rowDown) {
            String name = downElement.select("td").text();
            String[] split = name.split(" ");
            SysCitys sysCitys = returnCitys(split[0],split[2],parentCode,5,split[1]);
            saveDB(sysCitys);
//            sysAreas.add(sysCitys);
        }
        return sysAreas;
    }

    /**
     * 返回城市对象
     * @param addrCode
     * @param addrName
     * @param fatherCode
     * @return
     */
    private static SysCitys returnCitys(String addrCode, String addrName, String fatherCode, int level, String tcCode){
        SysCitys sysCitys = new SysCitys();
        sysCitys.setAreaCode(addrCode);
        sysCitys.setAreaName(addrName);
        sysCitys.setPAreaCode(fatherCode);
        sysCitys.setLevel(level);
        sysCitys.setTcCode(tcCode);
        return sysCitys;
    }

    /**
     * 获取网页数据
     */
    private static Document connect(String url) {
        Document document = connentExt(url);
        if (document == null) {
            if (retryTimes < 0) {
                throw new IllegalArgumentException("重试次数过多，中止数据抓取........");
            }
            retryTimes--;
            System.err.println("-------------------开始重试第"+ (100 - retryTimes) + "次");
            document = connentExt(url);
        }
        return document;
    }

    /**
     * 获取网页数据
     * @param url
     * @return
     */
    private static Document connentExt(String url) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("无效的url");
        }
        try {
            return Jsoup.connect(url).timeout(200 * 1000).get();
        } catch (IOException e) {
            System.out.println(url+"地址不存在");
            return null;
        }
    }
}
