package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import org.springframework.jdbc.core.JdbcOperations

class TormaystarkasteluDaoImpl(private val jdbcOperations: JdbcOperations) : TormaystarkasteluDao {

    override fun yleisetKatualueet(hankegeometriat: HankeGeometriat): Map<Int, Boolean> {
        with(jdbcOperations) {
            return query(
                """
            SELECT 
                tormays_ylre_parts_polys.fid,
                tormays_ylre_parts_polys.ylre_street_area,
                hankegeometria.id
            FROM
                tormays_ylre_parts_polys,
                hankegeometria
            WHERE
                hankegeometria.hankegeometriatid = ? AND
                st_overlaps(tormays_ylre_parts_polys.geom, hankegeometria.geometria);
        """.trimIndent(), { rs, _ ->
                    Pair(
                        rs.getInt(2) == 1,
                        rs.getInt(3)
                    )
                }, hankegeometriat.id!!
            ).associate { Pair(it.second, it.first) }
        }
    }

    override fun yleisetKatuluokat(hankegeometriat: HankeGeometriat): Map<Int, Set<TormaystarkasteluKatuluokka>> {
        val results = mutableMapOf<Int, MutableSet<TormaystarkasteluKatuluokka>>()
        with(jdbcOperations) {
            query(
                """
            SELECT 
                tormays_ylre_classes_polys.fid,
                tormays_ylre_classes_polys.ylre_class,
                hankegeometria.id
            FROM
                tormays_ylre_classes_polys,
                hankegeometria
            WHERE
                hankegeometria.hankegeometriatid = ? AND
                st_overlaps(tormays_ylre_classes_polys.geom, hankegeometria.geometria);
        """.trimIndent(), { rs, _ ->
                    val ylreClass = rs.getString(2)
                    Pair(
                        TormaystarkasteluKatuluokka.valueOfKatuluokka(ylreClass) ?: throw IllegalKatuluokkaException(
                            ylreClass
                        ),
                        rs.getInt(3)
                    )
                }, hankegeometriat.id!!
            ).forEach { pair ->
                results.computeIfAbsent(pair.second) { mutableSetOf() }.add(pair.first)
            }
        }
        return results
    }

    override fun katuluokat(hankegeometriat: HankeGeometriat): Map<Int, Set<TormaystarkasteluKatuluokka>> {
        val results = mutableMapOf<Int, MutableSet<TormaystarkasteluKatuluokka>>()
        with(jdbcOperations) {
            query(
                """
            SELECT 
                tormays_street_classes_polys.fid,
                tormays_street_classes_polys.street_class,
                hankegeometria.id
            FROM
                tormays_street_classes_polys,
                hankegeometria
            WHERE
                hankegeometria.hankegeometriatid = ? AND
                st_overlaps(tormays_street_classes_polys.geom, hankegeometria.geometria);
        """.trimIndent(), { rs, _ ->
                    val ylreClass = rs.getString(2)
                    Pair(
                        TormaystarkasteluKatuluokka.valueOfKatuluokka(ylreClass) ?: throw IllegalKatuluokkaException(
                            ylreClass
                        ),
                        rs.getInt(3)
                    )
                }, hankegeometriat.id!!
            ).forEach { pair ->
                results.computeIfAbsent(pair.second) { mutableSetOf() }.add(pair.first)
            }
        }
        return results
    }

    override fun kantakaupunki(hankegeometriat: HankeGeometriat): Map<Int, Boolean> {
        with(jdbcOperations) {
            return query(
                """
            SELECT 
                tormays_central_business_area_polys.fid,
                tormays_central_business_area_polys.central_business_area,
                hankegeometria.id
            FROM
                tormays_central_business_area_polys,
                hankegeometria
            WHERE
                hankegeometria.hankegeometriatid = ? AND
                st_overlaps(tormays_central_business_area_polys.geom, hankegeometria.geometria);
        """.trimIndent(), { rs, _ ->
                    Pair(
                        rs.getInt(2) == 1,
                        rs.getInt(3)
                    )
                }, hankegeometriat.id!!
            ).associate { Pair(it.second, it.first) }
        }
    }

    override fun liikennemaarat(
        hankegeometriat: HankeGeometriat,
        etaisyys: TormaystarkasteluLiikennemaaranEtaisyys
    ): Map<Int, Set<Int>> {
        val results = mutableMapOf<Int, MutableSet<Int>>()
        val tableName = "tormays_volumes${etaisyys.radius}_polys"
        with(jdbcOperations) {
            query(
                """
            SELECT
                $tableName.fid,
                $tableName.volume,
                hankegeometria.id
            FROM
                 $tableName,
                 hankegeometria
            WHERE
                hankegeometria.hankegeometriatid = ? AND
                st_overlaps($tableName.geom, hankegeometria.geometria);
        """.trimIndent(), { rs, _ ->
                    Pair(
                        rs.getInt(2),
                        rs.getInt(3)
                    )
                }, hankegeometriat.id!!
            ).forEach { pair ->
                results.computeIfAbsent(pair.second) { mutableSetOf() }.add(pair.first)
            }
        }
        return results
    }

    override fun bussiliikenteenKannaltaKriittinenAlue(hankegeometriat: HankeGeometriat): Map<Int, Boolean> {
        with(jdbcOperations) {
            return query(
                """
            SELECT 
                tormays_critical_area_polys.fid,
                tormays_critical_area_polys.critical_area,
                hankegeometria.id
            FROM
                tormays_critical_area_polys,
                hankegeometria
            WHERE
                hankegeometria.hankegeometriatid = ? AND
                st_overlaps(tormays_critical_area_polys.geom, hankegeometria.geometria);
        """.trimIndent(), { rs, _ ->
                    Pair(
                        rs.getInt(2) == 1,
                        rs.getInt(3)
                    )
                }, hankegeometriat.id!!
            ).associate { Pair(it.second, it.first) }
        }
    }

    override fun bussit(hankegeometriat: HankeGeometriat): Map<Int, Set<TormaystarkasteluBussireitti>> {
        val results = mutableMapOf<Int, MutableSet<TormaystarkasteluBussireitti>>()
        with(jdbcOperations) {
            query(
                """
                SELECT
                    tormays_buses_polys.fid,
                    tormays_buses_polys.route_id,
                    tormays_buses_polys.direction_id,
                    tormays_buses_polys.rush_hour,
                    tormays_buses_polys.trunk,
                    hankegeometria.id
                FROM
                    tormays_buses_polys,
                    hankegeometria
                WHERE
                    hankegeometria.hankegeometriatid = ? AND
                    st_overlaps(tormays_buses_polys.geom, hankegeometria.geometria);
                """.trimIndent(), { rs, _ ->
                    val geometriaId = rs.getInt(6)
                    val buses = results.computeIfAbsent(geometriaId) { mutableSetOf() }
                    buses.add(
                        TormaystarkasteluBussireitti(
                            rs.getString(2),
                            rs.getInt(3),
                            rs.getInt(4),
                            TormaystarkasteluBussiRunkolinja.valueOfRunkolinja(rs.getString(5))!!
                        )
                    )
                }, hankegeometriat.id!!
            )
        }
        return results
    }

    override fun raitiotiet(hankegeometriat: HankeGeometriat): Map<Int, Set<TormaystarkasteluRaitiotiekaistatyyppi>> {
        val results = mutableMapOf<Int, MutableSet<TormaystarkasteluRaitiotiekaistatyyppi>>()
        with(jdbcOperations) {
            query(
                """
            SELECT 
                tormays_trams_polys.fid,
                tormays_trams_polys.lane,
                hankegeometria.id
            FROM
                tormays_trams_polys,
                hankegeometria
            WHERE
                hankegeometria.hankegeometriatid = ? AND
                st_overlaps(tormays_trams_polys.geom, hankegeometria.geometria);
        """.trimIndent(), { rs, _ ->
                    val lane = rs.getString(2)
                    Pair(
                        TormaystarkasteluRaitiotiekaistatyyppi.valueOfKaistatyyppi(lane)
                            ?: throw IllegalKatuluokkaException(lane),
                        rs.getInt(3)
                    )
                }, hankegeometriat.id!!
            ).forEach { pair ->
                results.computeIfAbsent(pair.second) { mutableSetOf() }.add(pair.first)
            }
        }
        return results
    }

    override fun pyorailyreitit(hankegeometriat: HankeGeometriat): Map<Int, Set<TormaystarkasteluPyorailyreittiluokka>> {
        val results = mutableMapOf<Int, MutableSet<TormaystarkasteluPyorailyreittiluokka>>()
        with(jdbcOperations) {
            query(
                """
            SELECT 
                tormays_cycleways_priority_polys.fid,
                tormays_cycleways_priority_polys.cycleway,
                hankegeometria.id
            FROM 
                tormays_cycleways_priority_polys,
                hankegeometria
            WHERE
                hankegeometria.hankegeometriatid = ? AND
                st_overlaps(tormays_cycleways_priority_polys.geom, hankegeometria.geometria) 
            UNION
            SELECT 
                tormays_cycleways_main_polys.fid,
                tormays_cycleways_main_polys.cycleway,
                hankegeometria.id
            FROM
                tormays_cycleways_main_polys,
                hankegeometria
            WHERE
                hankegeometria.hankegeometriatid = ? AND
                st_overlaps(tormays_cycleways_main_polys.geom, hankegeometria.geometria);
        """.trimIndent(), { rs, _ ->
                    Pair(
                        TormaystarkasteluPyorailyreittiluokka.valueOfPyorailyvayla(rs.getString(2))
                            ?: TormaystarkasteluPyorailyreittiluokka.EI_PYORAILYREITTI,
                        rs.getInt(3)
                    )
                }, hankegeometriat.id!!, hankegeometriat.id!!
            ).forEach { pair ->
                results.computeIfAbsent(pair.second) { mutableSetOf() }.add(pair.first)
            }
        }
        return results
    }
}
