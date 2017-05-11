# Diplomová práce DP-NSS: Vyhledávací plugin Neo4j #

Plugin pro databázi Neo4j, který umožňuje parametrizovatelné vyhledávání spojů nad daty uložených jízdních řádů.

## Build ##
* Pro správný build je nutné mít nainstalovanou Javu 1.8

* Aplikace je buildovatelná mavenem. Sestavení provedeme příkazem

    ``mvn clean install``
    
## Zaregistrování v Neo4j ##
* Sestavením aplikace vznikne ve složce ``/target`` výsledný java archiv. Ten je nutné nahrát do složky ``${NEO4J_HOME}/plugins`` a databázi Neo4j restartovat. 

* Všechny procedury obsažené v pluginu bude od této chvíle možné volat pomocí jazyka Cypher, např.:

    ``CALL cz.cvut.dp.nss.search.initCalendarDates()``
