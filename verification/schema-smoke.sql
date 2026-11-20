-- Run against a disposable database after V1. All seed changes roll back.
BEGIN;
DO $$
DECLARE
    a uuid := gen_random_uuid(); f uuid := gen_random_uuid(); c uuid := gen_random_uuid();
    e uuid := gen_random_uuid(); r uuid := gen_random_uuid(); affected integer;
BEGIN
    INSERT INTO app_user VALUES (a,'smoke.aina','Aina','CLAIMANT','test-hash'),(f,'smoke.farah','Farah','OFFICER','test-hash');
    INSERT INTO claim(id,claimant_id,kind,market,currency,incident_on,description,status,version,submitted_at)
    VALUES (c,a,'MOTOR','MY','MYR',current_date,'Bumper','SUBMITTED',1,now());
    IF (SELECT reserve IS NOT NULL OR claim_ref IS NULL FROM claim WHERE id=c) THEN RAISE EXCEPTION 'Invalid initial state'; END IF;
    BEGIN
        UPDATE claim SET currency='SGD' WHERE id=c;
        RAISE EXCEPTION 'Expected invalid market/currency to fail';
    EXCEPTION WHEN check_violation THEN NULL; END;
    UPDATE claim SET officer_id=f,status='UNDER_REVIEW',version=2 WHERE id=c AND version=1 AND officer_id IS NULL;
    UPDATE claim SET officer_id=f,version=3 WHERE id=c AND version=1 AND officer_id IS NULL;
    GET DIAGNOSTICS affected=ROW_COUNT;
    IF affected<>0 THEN RAISE EXCEPTION 'Stale pickup unexpectedly succeeded'; END IF;
    INSERT INTO information_request(id,claim_id,question,asked_at) VALUES(r,c,'Estimate?',now());
    BEGIN
        INSERT INTO information_request(id,claim_id,question,asked_at) VALUES(gen_random_uuid(),c,'Another?',now());
        RAISE EXCEPTION 'Expected duplicate pending request to fail';
    EXCEPTION WHEN unique_violation THEN NULL; END;
    UPDATE information_request SET answer='5000.00',answered_at=now() WHERE id=r;
    UPDATE claim SET reserve=5000,version=5 WHERE id=c;
    INSERT INTO claim_event VALUES(e,c,5,f,'RESERVE_SET','STAFF_ONLY','{"current":"5000.00"}',now());
    BEGIN
        INSERT INTO claim_event VALUES(gen_random_uuid(),c,5,f,'RESERVE_SET','STAFF_ONLY','{}',now());
        RAISE EXCEPTION 'Expected duplicate claim version to fail';
    EXCEPTION WHEN unique_violation THEN NULL; END;
    INSERT INTO outbox VALUES(e,jsonb_build_object('eventId',e,'claimId',c,'claimRef',(SELECT claim_ref FROM claim WHERE id=c),'claimVersion',5,'currency','MYR','isOpen',true,'reserve','5000.00'),NULL);
    INSERT INTO exposure_snapshot VALUES(c,5,'MYR',true,5000,now());
    INSERT INTO exposure_snapshot VALUES(c,3,'MYR',true,NULL,now())
    ON CONFLICT (claim_id) DO UPDATE SET reserve=excluded.reserve,claim_version=excluded.claim_version
    WHERE exposure_snapshot.claim_version<excluded.claim_version;
    IF (SELECT reserve IS DISTINCT FROM 5000 FROM exposure_snapshot WHERE claim_id=c) THEN RAISE EXCEPTION 'Stale event applied'; END IF;
    UPDATE claim SET status='SETTLED',settlement=4200,reserve=0,closed_at=now(),version=6 WHERE id=c;
    BEGIN
        UPDATE claim SET settlement=NULL WHERE id=c;
        RAISE EXCEPTION 'Expected missing settlement to fail';
    EXCEPTION WHEN check_violation THEN NULL; END;
END $$;
ROLLBACK;
