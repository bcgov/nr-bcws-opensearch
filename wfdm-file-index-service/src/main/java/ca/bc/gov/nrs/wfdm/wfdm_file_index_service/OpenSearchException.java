package ca.bc.gov.nrs.wfdm.wfdm_file_index_service;

public class OpenSearchException extends Exception {
    private static final long serialVersionUID = 1L;

    public OpenSearchException(Throwable t) {
        super(t);
    }

    public OpenSearchException(String message, Throwable t) {
        super(message, t);
    }
}
