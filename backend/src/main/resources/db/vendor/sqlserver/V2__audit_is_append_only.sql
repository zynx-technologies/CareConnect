CREATE TRIGGER trg_audit_event_append_only ON audit_event
INSTEAD OF UPDATE, DELETE
AS
BEGIN
    THROW 51000, 'audit_event is append-only', 1;
END;
