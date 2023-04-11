
-- 1、五级行政区域划分
CREATE TABLE `area` (
                        `area_code` varchar(50) CHARACTER SET utf8mb4 DEFAULT NULL,
                        `area_name` varchar(100) DEFAULT NULL,
                        `p_area_code` varchar(50) DEFAULT NULL,
                        `level` int(2) DEFAULT NULL,
                        `tc_code` varchar(10) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8;