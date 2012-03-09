CREATE TABLE `jdousergroup` (
  `ID` bigint(20) NOT NULL,
  `CREATION_DATE` datetime DEFAULT NULL,
  `ETAG` bigint(20) NOT NULL,
  `ISINDIVIDUAL` bit(1) DEFAULT NULL,
  `NAME` varchar(256) CHARACTER SET latin1 COLLATE latin1_bin DEFAULT NULL,
  PRIMARY KEY (`ID`),
  UNIQUE KEY `JDOUSERGROUP_U1` (`NAME`)
)