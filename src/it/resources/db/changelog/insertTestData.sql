insert into io_ehr
(io_ehr_id, name, client_id, public_key, private_key)
values ( 101, 'EPIC', 'a3da9a08-4fd4-443b-b0f5-6226547a98db', 'public', '${AO_SANDBOX_KEY}')
GO

insert into io_tenant
(io_tenant_id, mnemonic, io_ehr_id, available_batch_start, available_batch_end)
values ( 1001, 'apposnd', 101, '22:00:00',  '06:00:00')
GO

insert into io_tenant_epic
(io_tenant_id, service_endpoint, ehr_user_id, release_version, message_type, practitioner_provider_system, practitioner_user_system)
values ( 1001, 'https://apporchard.epic.com/interconnect-aocurprd-oauth', 1, '1.0', '1', 'urn:oid:1.2.840.114350.1.13.0.1.7.2.836982', 'urn:oid:1.2.840.114350.1.13.0.1.7.2.697780')
GO

insert into io_tenant_provider_pool
(io_tenant_provider_pool_id, io_tenant_id, provider_id, pool_id)
values (10001 , 1001, "ProviderWithPool", "14600")
GO
