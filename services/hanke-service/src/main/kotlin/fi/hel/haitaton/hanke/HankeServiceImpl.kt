package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeSearch
import fi.hel.haitaton.hanke.domain.HankeYhteystieto

import mu.KotlinLogging
import org.springframework.security.core.context.SecurityContextHolder
import java.time.LocalDate
import java.time.ZonedDateTime

private val logger = KotlinLogging.logger { }

open class HankeServiceImpl(private val hankeRepository: HankeRepository, private val hanketunnusService: HanketunnusService) : HankeService {


    // TODO:
    /*
       - a structure to return a list of references to Hanke and their savetypes (i.e. can contain up to 3 saves for the same hanke)
       -- there could be all 3 simultaneously, and any combination ...
       -- UI should show about autosave if (autosave exists and is newer than draft/submit and is different from draft/submit);
           it should show either a draft or submit, whichever is newest (if there is a draft, it should be newer than submit, since submit deletes drafts and autosaves)
       - a method to query all hankkeet for a given user..
            findAllHankeForUser(userId: String): SpecialListOf ...
       - a method to query the current savestates for given hanke
           getHankeSaves(hankeId: Long): SpecialListOf ...
            getHankeSaves(hankeTunnus: String): SpecialListOf ...

       - createHanke can do either autosave or draft, not submit (not enough data for that)
       - updateHanke can do any save mode.

       - change loadHanke(tunnus) to return latest non-autosave (either draft or submit)
       - loadHanke(tunnus, savetype)
       - loadHanke(id, savetype)
     */

    override fun loadAllHanke(hankeSearch: HankeSearch?): List<Hanke> {

        // TODO: user token  from front?
        // TODO: do we limit result for user own hanke?

        return if (hankeSearch == null || hankeSearch.isEmpty()) {
            loadAllHanke()
        } else if (hankeSearch.saveType != null) {
            loadAllHankeWithSavetype(hankeSearch.saveType)
        } else {
            //  Get all hanke datas within time period (= either or both of alkuPvm and loppuPvm are inside the requested period)
            loadAllHankeBetweenDates(hankeSearch.periodBegin!!, hankeSearch.periodEnd!!)
        }
    }

    // WITH THIS ONE CAN AUTHORIZE ONLY THE OWNER TO LOAD A HANKE: @PostAuthorize("returnObject.createdBy == authentication.name")
    override fun loadHanke(hankeTunnus: String): Hanke? {
        // TODO: Find out all savetype matches and return the more recent draft vs. submit.
        val entity = hankeRepository.findByHankeTunnus(hankeTunnus)
        if (entity == null)
            return null

        if (entity.id == null) {
            throw DatabaseStateException(hankeTunnus)
        }

        return createHankeDomainObjectFromEntity(entity)
    }


    /**
     * Returns all the Hanke items from database for now
     *
     * Returns empty list if no items to return
     * TODO user information to limit what all Hanke items we get?
     */
    internal fun loadAllHanke(): List<Hanke> {

        return hankeRepository.findAll().map { createHankeDomainObjectFromEntity(it) }
    }

    /**
     * Returns all the Hanke items for which the hanke period overlaps with the given period.
     *
     * Returns empty list if no items to return
     * TODO user information to limit what all Hanke items we get?
     */
    internal fun loadAllHankeBetweenDates(periodBegin: LocalDate, periodEnd: LocalDate): List<Hanke> {

        //Hanke ends must be after period start and hanke starts before period ends (that's the reason for parameters going in reversed)
        return hankeRepository.findAllByAlkuPvmIsBeforeAndLoppuPvmIsAfter(periodEnd, periodBegin).map { createHankeDomainObjectFromEntity(it) }
    }

    /**
     * Returns all the Hanke items for which the saveType is the wanted
     *
     * Returns empty list if no items to return
     * TODO user information to limit what all Hanke items we get?
     */
    internal fun loadAllHankeWithSavetype(saveType: SaveType): List<Hanke> {
        return hankeRepository.findAllBySaveType(saveType).map { createHankeDomainObjectFromEntity(it) }
    }

    /**
     * @return a new Hanke instance with the added and possibly modified values.
     */
    override fun createHanke(hanke: Hanke): Hanke {
        // TODO: Only create that hanke-tunnus if a specific set of fields are non-empty/set.
        //   For now, hanke-tunnus is created as soon as this function is called, even for fully empty data.

        val userid = SecurityContextHolder.getContext().authentication.name

        // Create the entity object and save it (first time) to get db-id
        val entity = HankeEntity()
        copyNonNullHankeFieldsToEntity(hanke, entity)
        copyYhteystietosToEntity(hanke, entity, userid)
        // NOTE: flags are NOT copied from incoming data, as they are set by internal logic.
        // Special fields; handled "manually".. TODO: see if some framework could handle (some of) these for us automatically
        entity.version = 0
        entity.createdByUserId = userid
        entity.createdAt = getCurrentTimeUTCAsLocalTime()
        entity.modifiedByUserId = null
        entity.modifiedAt = null

        val hanketunnus = hanketunnusService.newHanketunnus()
        entity.hankeTunnus = hanketunnus
        logger.debug {
            "Creating Hanke ${hanke.hankeTunnus}: ${hanke.toLogString()}"
        }
        hankeRepository.save(entity)
        logger.debug {
            "Created Hanke ${hanke.hankeTunnus}."
        }
        // Creating a new domain object for the return value; it will have the updated values from the database,
        // e.g. the main date values truncated to midnight, and the added id and hanketunnus.
        return createHankeDomainObjectFromEntity(entity)
    }

    // WITH THIS ONE CAN AUTHORIZE ONLY THE OWNER TO UPDATE A HANKE: @PreAuthorize("#hanke.createdBy == authentication.name")
    override fun updateHanke(hanke: Hanke): Hanke {
        if (hanke.hankeTunnus == null)
            error("Somehow got here with hanke without hanke-tunnus")

        // Both checks that the hanke already exists, and get its old fields to transfer data into
        val entity = hankeRepository.findByHankeTunnus(hanke.hankeTunnus!!)
                ?: throw HankeNotFoundException(hanke.hankeTunnus!!)
        val userid = SecurityContextHolder.getContext().authentication.name
        // Transfer field values from domain object to entity object, and set relevant audit fields:
        copyNonNullHankeFieldsToEntity(hanke, entity)
        copyYhteystietosToEntity(hanke, entity, userid)
        // NOTE: flags are NOT copied from incoming data, as they are set by internal logic.
        // Special fields; handled "manually".. TODO: see if some framework could handle (some of) these for us automatically
        entity.version = entity.version?.inc() ?: 1
        // (Not changing createdBy/At fields.)
        entity.modifiedByUserId = userid
        entity.modifiedAt = getCurrentTimeUTCAsLocalTime()

        logger.debug {
            "Saving Hanke ${hanke.hankeTunnus}: ${hanke.toLogString()}"
        }
        hankeRepository.save(entity)
        logger.debug {
            "Saved Hanke ${hanke.hankeTunnus}."
        }

        // Creating a new domain object for the return value; it will have the updated values from the database,
        // e.g. the main date values truncated to midnight.
        return createHankeDomainObjectFromEntity(entity)
    }

    override fun updateHankeStateFlags(hanke: Hanke) {
        if (hanke.hankeTunnus == null) {
            error("Somehow got here with hanke without hanke-tunnus")
        }
        // Both checks that the hanke already exists, and get its old fields to transfer data into
        val entity = hankeRepository.findByHankeTunnus(hanke.hankeTunnus!!)
                ?: throw HankeNotFoundException(hanke.hankeTunnus!!)
        copyStateFlagsToEntity(hanke, entity)
        hankeRepository.save(entity)
    }

    // ======================================================================

    // --------------- Helpers for data transfer from database ------------

    companion object Converters {
        internal fun createHankeDomainObjectFromEntity(hankeEntity: HankeEntity): Hanke {
            val h = Hanke(
                    hankeEntity.id,
                    hankeEntity.hankeTunnus,
                    hankeEntity.onYKTHanke,
                    hankeEntity.nimi,
                    hankeEntity.kuvaus,
                    hankeEntity.alkuPvm?.atStartOfDay(TZ_UTC),
                    hankeEntity.loppuPvm?.atStartOfDay(TZ_UTC),
                    hankeEntity.vaihe,
                    hankeEntity.suunnitteluVaihe,

                    hankeEntity.version,
                    // TODO: will need in future to actually fetch the username from another service.. (or whatever we choose to pass out here)
                    //   Do it below, outside this construction call.
                    hankeEntity.createdByUserId ?: "",
                    // From UTC without timezone info to UTC with timezone info
                    if (hankeEntity.createdAt != null) ZonedDateTime.of(hankeEntity.createdAt, TZ_UTC) else null,
                    hankeEntity.modifiedByUserId,
                    if (hankeEntity.modifiedAt != null) ZonedDateTime.of(hankeEntity.modifiedAt, TZ_UTC) else null,

                    hankeEntity.saveType
            )
            createSeparateYhteystietolistsFromEntityData(h, hankeEntity)

            h.tyomaaKatuosoite = hankeEntity.tyomaaKatuosoite
            h.tyomaaTyyppi = hankeEntity.tyomaaTyyppi
            h.tyomaaKoko = hankeEntity.tyomaaKoko

            h.haittaAlkuPvm = hankeEntity.haittaAlkuPvm?.atStartOfDay(TZ_UTC)
            h.haittaLoppuPvm = hankeEntity.haittaLoppuPvm?.atStartOfDay(TZ_UTC)
            h.kaistaHaitta = hankeEntity.kaistaHaitta
            h.kaistaPituusHaitta = hankeEntity.kaistaPituusHaitta
            h.meluHaitta = hankeEntity.meluHaitta
            h.polyHaitta = hankeEntity.polyHaitta
            h.tarinaHaitta = hankeEntity.tarinaHaitta

            copyStateFlagsFromEntity(h, hankeEntity)
            h.updateStateFlags()

            return h
        }

        /**
         * createSeparateYhteystietolistsFromEntityData splits entity's one list to three different contact information
         * lists and adds them for Hanke domain object
         */
        private fun createSeparateYhteystietolistsFromEntityData(hanke: Hanke, hankeEntity: HankeEntity) {

            hankeEntity.listOfHankeYhteystieto.forEach { hankeYhteysEntity ->
                val hankeYhteystieto = createHankeYhteystietoDomainObjectFromEntity(hankeYhteysEntity)

                if (hankeYhteysEntity.contactType == ContactType.OMISTAJA)
                    hanke.omistajat.add(hankeYhteystieto)
                if (hankeYhteysEntity.contactType == ContactType.TOTEUTTAJA)
                    hanke.toteuttajat.add(hankeYhteystieto)
                if (hankeYhteysEntity.contactType == ContactType.ARVIOIJA)
                    hanke.arvioijat.add(hankeYhteystieto)
            }
        }

        private fun createHankeYhteystietoDomainObjectFromEntity(hankeYhteystietoEntity: HankeYhteystietoEntity): HankeYhteystieto {
            var createdAt: ZonedDateTime? = null

            if (hankeYhteystietoEntity.createdAt != null)
                createdAt = ZonedDateTime.of(hankeYhteystietoEntity.createdAt, TZ_UTC)

            var modifiedAt: ZonedDateTime? = null
            if (hankeYhteystietoEntity.modifiedAt != null)
                modifiedAt = ZonedDateTime.of(hankeYhteystietoEntity.modifiedAt, TZ_UTC)

            return HankeYhteystieto(
                    id = hankeYhteystietoEntity.id,
                    etunimi = hankeYhteystietoEntity.etunimi,
                    sukunimi = hankeYhteystietoEntity.sukunimi,
                    email = hankeYhteystietoEntity.email,
                    puhelinnumero = hankeYhteystietoEntity.puhelinnumero,
                    organisaatioId = hankeYhteystietoEntity.organisaatioId,
                    organisaatioNimi = hankeYhteystietoEntity.organisaatioNimi,
                    osasto = hankeYhteystietoEntity.osasto,
                    createdBy = hankeYhteystietoEntity.createdByUserId,
                    modifiedBy = hankeYhteystietoEntity.modifiedByUserId,
                    createdAt = createdAt,
                    modifiedAt = modifiedAt
            )
        }

        private fun copyStateFlagsFromEntity(h: Hanke, entity: HankeEntity) {
            h.tilat.onGeometrioita = entity.tilaOnGeometrioita
            h.tilat.onViereisiaHankkeita = entity.tilaOnViereisiaHankkeita
            h.tilat.onAsiakasryhmia = entity.tilaOnAsiakasryhmia
        }

    }

    // --------------- Helpers for data transfer towards database ------------

    private fun copyStateFlagsToEntity(hanke: Hanke, entity: HankeEntity) {
        entity.tilaOnGeometrioita = hanke.tilat.onGeometrioita
        entity.tilaOnViereisiaHankkeita = hanke.tilat.onViereisiaHankkeita
        entity.tilaOnAsiakasryhmia = hanke.tilat.onAsiakasryhmia
    }

    /**
     * Does NOT copy the id and hankeTunnus fields because one is supposed to find
     * the HankeEntity instance from the database with those values, and after that,
     * the values are filled by the database and should not be changed.
     * Also, version, createdByUserId, createdAt, modifiedByUserId, modifiedAt, version are not
     * set here, as they are to be set internally, and depends on which operation
     * is being done.
     */
    private fun copyNonNullHankeFieldsToEntity(hanke: Hanke, entity: HankeEntity) {
        hanke.onYKTHanke?.let { entity.onYKTHanke = hanke.onYKTHanke }
        hanke.nimi?.let { entity.nimi = hanke.nimi }
        hanke.kuvaus?.let { entity.kuvaus = hanke.kuvaus }
        // Assuming the incoming date, while being zoned date and time, is in UTC and time value can be simply dropped here.
        // Note, .toLocalDate() does not do any time zone conversion.
        hanke.alkuPvm?.let { entity.alkuPvm = hanke.alkuPvm?.toLocalDate() }
        hanke.loppuPvm?.let { entity.loppuPvm = hanke.loppuPvm?.toLocalDate() }
        hanke.vaihe?.let { entity.vaihe = hanke.vaihe }
        hanke.suunnitteluVaihe?.let { entity.suunnitteluVaihe = hanke.suunnitteluVaihe }

        hanke.saveType?.let { entity.saveType = hanke.saveType }
        hanke.tyomaaKatuosoite?.let { entity.tyomaaKatuosoite = hanke.tyomaaKatuosoite }
        entity.tyomaaTyyppi = hanke.tyomaaTyyppi
        hanke.tyomaaKoko?.let { entity.tyomaaKoko = hanke.tyomaaKoko }

        // Assuming the incoming date, while being zoned date and time, is in UTC and time value can be simply dropped here.
        // Note, .toLocalDate() does not do any time zone conversion.
        hanke.haittaAlkuPvm?.let { entity.haittaAlkuPvm = hanke.haittaAlkuPvm?.toLocalDate() }
        hanke.haittaLoppuPvm?.let { entity.haittaLoppuPvm = hanke.haittaLoppuPvm?.toLocalDate() }
        hanke.kaistaHaitta?.let { entity.kaistaHaitta = hanke.kaistaHaitta }
        hanke.kaistaPituusHaitta?.let { entity.kaistaPituusHaitta = hanke.kaistaPituusHaitta }
        hanke.meluHaitta?.let { entity.meluHaitta = hanke.meluHaitta }
        hanke.polyHaitta?.let { entity.polyHaitta = hanke.polyHaitta }
        hanke.tarinaHaitta?.let { entity.tarinaHaitta = hanke.tarinaHaitta }

    }

    /**
     * Transfer yhteystieto fields from domain to (new or existing) entity object,
     * combine the three lists into one list, and set the audit fields as relevant.
     */
    private fun copyYhteystietosToEntity(hanke: Hanke, entity: HankeEntity, userid: String) {
        // Note, if the incoming data indicates it is an already saved yhteystieto (id-field is set), should try
        // to transfer the business fields to the same old corresponding entity. Pretty much a must in order to
        // preserve createdBy and createdAt field values without having to rely on the client-side to hold
        // the values for us (bad design), which would also require checks on those (to prevent tampering).

        // Create temporary id-to-existingyhteystieto -map, used to find quickly whether an incoming Yhteystieto is new or exists already.
        // YT = Yhteystieto
        val existingYTs: MutableMap<Int, HankeYhteystietoEntity> = mutableMapOf()
        for (existingYT in entity.listOfHankeYhteystieto) {
            if (existingYT.id == null) {
                throw DatabaseStateException("A persisted HankeYhteystietoEntity somehow missing id, Hanke id ${entity.id}")
            } else {
                existingYTs[existingYT.id!!] = existingYT
            }
        }

        // Check each incoming yhteystieto (from three lists) for being new or an update to existing one,
        // and add to the main entity's single list if necessary:
        processIncomingHankeYhteystietosOfSpecificTypeToEntity(hanke.omistajat, entity, ContactType.OMISTAJA, userid, existingYTs)
        processIncomingHankeYhteystietosOfSpecificTypeToEntity(hanke.arvioijat, entity, ContactType.ARVIOIJA, userid, existingYTs)
        processIncomingHankeYhteystietosOfSpecificTypeToEntity(hanke.toteuttajat, entity, ContactType.TOTEUTTAJA, userid, existingYTs)

        // TODO: this method of removing entries if they are missing in the incoming data is different to
        //     behavior of the other simpler fields, where missing or null field is considered "keep the existing value,
        //     and return it back in response".
        //     However, those simpler fields can not be removed as a whole, so they _can_ behave so.
        //     For clarity, yhteystieto-entries should have separate action for removal (but then they should also
        //     have separate action for addition, i.e. own API endpoint in controller).
        //     So, consider the code below as "for now".
        // If there is anything left in the existingYTs map, they have been removed in the incoming data,
        // so remove them from the entity's list and make the back-reference null (and thus delete from the database).
        // TODO: Yhteystietos should not be in a list, but a "bag", or ensure it is e.g. linkedlist instead of arraylist (or similars).
        //    The order of Yhteystietos does not matter(?), and removing things from e.g. array list
        //    gets inefficient. Since there are so few entries, this crude solution works, for now.
        for (hankeYht in existingYTs.values) {
            entity.removeYhteystieto(hankeYht)
        }
    }


    private fun processIncomingHankeYhteystietosOfSpecificTypeToEntity(
            listOfHankeYhteystiedot: List<HankeYhteystieto>, hankeEntity: HankeEntity, contactType: ContactType,
            userid: String, existingYTs: MutableMap<Int, HankeYhteystietoEntity>) {
        for (hankeYht in listOfHankeYhteystiedot) {
            val someFieldsSet = hankeYht.isAnyFieldSet()
            var validYhteystieto = false
            // The UI has currently bigger problems implementing it so that Yhteystieto entries that haven't even been touched
            //   would not be sent to backend as a group of ""-fields, so the condition here is to make
            //   such fully-empty entry considered as non-existent (i.e. skip it).
            //   Also, for now, if anything is given, it is checked that all 4 mandatory fields are given, or we log an error and skip it.
            // Note, validator should have enforced that at least the four main fields are all set (or all of them are empty).
            // TODO: Currently validator does allow through an entry with four mandatory fields empty, but organisation field(s) non-empty!
            // If any field is given (not empty and not only whitespace)...
            if (someFieldsSet) {
                // TODO: Logic has been changed to allow saving the fields separately (or added one at a time)
                //   Seems the process needs some more thinking through to ensure valid final situation.
                validYhteystieto = hankeYht.isAnyFieldSet()
                // Check if at least the 4 mandatory fields are given
                //validYhteystieto = hankeYht.isValid()
            }

            // Is the incoming Yhteystieto new (does not have id, create new) or old (has id, update existing)?
            if (hankeYht.id == null) {
                // New Yhteystieto
                processCreateYhteystieto(hankeYht, validYhteystieto, contactType, userid, hankeEntity)
            } else {
                // Should be an existing Yhteystieto
                processUpdateYhteystieto(hankeYht, existingYTs, someFieldsSet, validYhteystieto, userid, hankeEntity)
            }
        }
    }

    private fun processCreateYhteystieto(hankeYht: HankeYhteystieto, validYhteystieto: Boolean, contactType: ContactType,
                                         userid: String, hankeEntity: HankeEntity) {
        if (validYhteystieto) {
            // ... it is valid, so create a new Yhteystieto
            val hankeYhtEntity = HankeYhteystietoEntity(
                    contactType,
                    hankeYht.sukunimi,
                    hankeYht.etunimi,
                    hankeYht.email,
                    hankeYht.puhelinnumero,
                    hankeYht.organisaatioId,
                    hankeYht.organisaatioNimi,
                    hankeYht.osasto,

                    userid, // createdByUserId
                    getCurrentTimeUTCAsLocalTime(), // createdAt
                    null,
                    null,
                    null, // will be set by the database
                    hankeEntity) // reference back to parent hanke
            hankeEntity.addYhteystieto(hankeYhtEntity)
        } else {
            // ... missing some mandatory fields, should not have gotten here. Log it and skip it.
            logger.error {
                "Got a new Yhteystieto object with one or more empty mandatory fields, skipping it. HankeId ${hankeEntity.id}"
            }
        }
    }

    private fun processUpdateYhteystieto(hankeYht: HankeYhteystieto, existingYTs: MutableMap<Int, HankeYhteystietoEntity>,
                                         someFieldsSet: Boolean, validYhteystieto: Boolean, userid: String, hankeEntity: HankeEntity) {
        // If incoming Yhteystieto has id set, it _should_ be among the existing Yhteystietos, or some kind of error has happened.
        val incomingId: Int = hankeYht.id!!
        val existingYT: HankeYhteystietoEntity? = existingYTs[incomingId]
        if (existingYT == null) {
            // Some sort of error situation;
            // - simultaneous edits to the same hanke by someone else (the Yhteystieto could have been removed in the database)
            // - the incoming ids are for different hanke (i.e. incorrect data in the incoming request)
            throw HankeYhteystietoNotFoundException(hankeEntity.id!!, incomingId)
        }

        if (validYhteystieto) {
            // All required fields found, so update existing entity fields:
            existingYT.sukunimi = hankeYht.sukunimi
            existingYT.etunimi = hankeYht.etunimi
            existingYT.email = hankeYht.email
            existingYT.puhelinnumero = hankeYht.puhelinnumero
            existingYT.organisaatioId = hankeYht.organisaatioId
            hankeYht.organisaatioNimi?.let { existingYT.organisaatioNimi = hankeYht.organisaatioNimi }
            hankeYht.osasto?.let { existingYT.osasto = hankeYht.osasto }

            // (Not changing createdBy/At fields)
            existingYT.modifiedByUserId = userid
            existingYT.modifiedAt = getCurrentTimeUTCAsLocalTime()
            // (Not touching the id or hanke fields)

            // No need to add the existing Yhteystieto entity to the hanke's list; it is already in it.
            // Remove the corresponding entry from the map. (Afterwards, the entries remaining in the map
            // were not in the incoming data, so should be removed from the database.)
            existingYTs.remove(incomingId)
        } else {
            // Trying to update an Yhteystieto with one or more empty mandatory data fields.
            // If we do not change anything, the response will send back to previous stored values.
            // TODO: Check if the above operation is ok?
            // However, checking one special case; all fields being empty. This corresponds to initial state,
            // where the corresponding Yhteystieto is not set. Therefore, for now, considering it as "please delete".
            // (Handling it by reversed logic using the existingYTs map, see above.)
            if (someFieldsSet) {
                logger.error {
                    "Got a new Yhteystieto object with one or more empty mandatory fields, skipping it. HankeId ${hankeEntity.id}"
                }
                existingYTs.remove(incomingId)
            }
            // If the entry was left in the existingYTs, it will get deleted.
        }
    }
}
