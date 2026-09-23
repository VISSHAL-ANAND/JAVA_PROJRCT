package service;

import dao.ResourceDAO;
import exception.ResourceException;

import java.sql.SQLException;
import java.util.List;

public class ResourceService {
    private final ResourceDAO resourceDAO;

    public ResourceService(ResourceDAO resourceDAO) {
        this.resourceDAO = resourceDAO;
    }

    public List<Integer> getAvailableResourceIds() throws ResourceException {
        try {
            return resourceDAO.findAvailableResourceIds();
        } catch (SQLException e) {
            throw new ResourceException("Unable to load available resources");
        }
    }
}
