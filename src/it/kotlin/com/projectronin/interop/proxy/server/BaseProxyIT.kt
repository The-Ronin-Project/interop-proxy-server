package com.projectronin.interop.proxy.server

import com.projectronin.interop.common.http.spring.HttpSpringConfig
import com.projectronin.interop.common.vendor.VendorType
import com.projectronin.interop.tenant.config.data.CernerTenantDAO
import com.projectronin.interop.tenant.config.data.EhrDAO
import com.projectronin.interop.tenant.config.data.EpicTenantDAO
import com.projectronin.interop.tenant.config.data.TenantDAO
import com.projectronin.interop.tenant.config.data.binding.CernerTenantDOs
import com.projectronin.interop.tenant.config.data.binding.EhrDOs
import com.projectronin.interop.tenant.config.data.binding.EpicTenantDOs
import com.projectronin.interop.tenant.config.data.binding.MirthTenantConfigDOs
import com.projectronin.interop.tenant.config.data.binding.ProviderPoolDOs
import com.projectronin.interop.tenant.config.data.binding.TenantCodesDOs
import com.projectronin.interop.tenant.config.data.binding.TenantDOs
import com.projectronin.interop.tenant.config.data.binding.TenantMDMConfigDOs
import com.projectronin.interop.tenant.config.data.binding.TenantServerDOs
import com.projectronin.interop.tenant.config.data.model.CernerTenantDO
import com.projectronin.interop.tenant.config.data.model.EhrDO
import com.projectronin.interop.tenant.config.data.model.EpicTenantDO
import com.projectronin.interop.tenant.config.data.model.TenantDO
import org.junit.jupiter.api.AfterEach
import org.ktorm.database.Database
import org.ktorm.dsl.deleteAll
import java.time.LocalTime
import java.time.ZoneId
import java.util.stream.Stream

abstract class BaseProxyIT {
    companion object {
        @JvmStatic
        fun tenantMnemonics(): Stream<String> {
            return Stream.of("epic", "cerner")
        }
    }

    protected val serverUrl = "http://localhost:8080"
    protected val httpClient = HttpSpringConfig().getHttpClient()
    protected val tenantDB = Database.connect(url = "jdbc:mysql://ehr:ThePassword@localhost:3306/tenant-db")
    protected val ehrDAO = EhrDAO(tenantDB)
    protected val tenantDAO = TenantDAO(tenantDB)
    // open val resources = mapOf<String, KClass<out Resource>>()

    @AfterEach
    fun cleanup() {
        purgeTenantData()
    }

    protected fun purgeTenantData() {
        tenantDB.deleteAll(TenantServerDOs)
        tenantDB.deleteAll(TenantCodesDOs)
        tenantDB.deleteAll(ProviderPoolDOs)
        tenantDB.deleteAll(MirthTenantConfigDOs)
        tenantDB.deleteAll(TenantMDMConfigDOs)
        tenantDB.deleteAll(EpicTenantDOs)
        tenantDB.deleteAll(CernerTenantDOs)
        tenantDB.deleteAll(TenantServerDOs)
        tenantDB.deleteAll(TenantDOs)
        tenantDB.deleteAll(EhrDOs)
    }

    protected fun populateTenantData() {
        val epicTenantDAO = EpicTenantDAO(tenantDB)
        val cernerTenantDAO = CernerTenantDAO(tenantDB)
        val epicEHRDO =
            ehrDAO.insert(
                EhrDO {
                    id = 101
                    instanceName = "Epic Sandbox"
                    clientId = "clientID"
                    publicKey = "publicKey"
                    privateKey = System.getenv("AO_SANDBOX_KEY")
                    vendorType = VendorType.EPIC
                },
            )
        val cernerEHRDo =
            ehrDAO.insert(
                EhrDO {
                    id = 202
                    instanceName = "Cerner Sandbox"
                    clientId = "clientID"
                    accountId = "accountId"
                    secret = "secret"
                    vendorType = VendorType.CERNER
                },
            )
        val mockEpicDo =
            tenantDAO.insertTenant(
                TenantDO {
                    id = 1001
                    name = "Mock Epic Hospital"
                    ehr = epicEHRDO
                    mnemonic = "epic"
                    availableBatchEnd = LocalTime.parse("06:00:00")
                    availableBatchEnd = LocalTime.parse("22:00:00")
                    timezone = ZoneId.of("Etc/UTC")
                    monitoredIndicator = true
                },
            )
        epicTenantDAO.insert(
            EpicTenantDO {
                tenantId = mockEpicDo.id
                release = "1.0"
                serviceEndpoint = "http://mockehr:8080/epic"
                authEndpoint = "http://mockehr:8080/epic/oauth2/token"
                ehrUserId = "2"
                messageType = "1"
                practitionerProviderSystem = "mockEHRProviderSystem"
                practitionerUserSystem = "mockEHRUserSystem"
                patientMRNSystem = "mockEHRMRNSystem"
                patientInternalSystem = "mockPatientInternalSystem"
                encounterCSNSystem = "mockEncounterCSNSystem"
                patientMRNTypeText = "MRN"
                departmentInternalSystem = "mockEHRDepartmentInternalSystem"
                patientOnboardedFlagId = "135124"
                orderSystem = "mockEHROrderSystem"
            },
        )

        val mockCernerDO =
            tenantDAO.insertTenant(
                TenantDO {
                    id = 2002
                    name = "Mock Cerner Hospital"
                    ehr = cernerEHRDo
                    mnemonic = "cerner"
                    availableBatchEnd = LocalTime.parse("06:00:00")
                    availableBatchEnd = LocalTime.parse("22:00:00")
                    timezone = ZoneId.of("Etc/UTC")
                },
            )
        cernerTenantDAO.insert(
            CernerTenantDO {
                tenantId = mockCernerDO.id
                serviceEndpoint = "http://mockehr:8080/cerner/fhir/r4"
                authEndpoint = "http://mockehr:8080/cerner/oauth2/token"
                patientMRNSystem = "mockEHRMRNSystem"
            },
        )
        tenantDB.transactionManager
    }
}
