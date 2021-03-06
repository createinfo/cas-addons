package net.unicon.cas.addons.serviceregistry;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.commons.io.IOUtils;
import org.jasig.cas.services.AbstractRegisteredService;
import org.jasig.cas.services.RegisteredService;
import org.springframework.core.io.Resource;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * An extension of the JsonServiceRegistryDao that is able to support both read/write operations when
 * saving or deleting registered services.
 * <p/>
 * Note: This implementation is NOT transactional nor is it thread-safe. No such implementation can be possible on top
 * of an ordinary file system.
 *
 * @author Misagh Moayyed
 * @author Unicon, inc.
 * @since 1.6
 */
public final class ReadWriteJsonServiceRegistryDao extends JsonServiceRegistryDao {

    public ReadWriteJsonServiceRegistryDao(final Resource servicesConfigFile) {
        super(servicesConfigFile);
        this.objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
    }

    @Override
    protected RegisteredService saveInternal(final RegisteredService registeredService) {
        logger.debug("Loading service definitions from resource [{}]", this.servicesConfigFile.getFilename());
        final List<RegisteredService> resolvedServices = super.loadServices();
        final List<RegisteredService> col = new ArrayList<RegisteredService>(resolvedServices);

        if (registeredService.getId() < 0) {
            if (registeredService instanceof AbstractRegisteredService) {
                final Random random = new Random(registeredService.hashCode());
                final int serviceId = random.nextInt(Integer.MAX_VALUE);
                ((AbstractRegisteredService) registeredService).setId(serviceId);
            }
        }
        boolean foundAndRemovedService = false;
        final Iterator<RegisteredService> it = col.iterator();
        while(!foundAndRemovedService && it.hasNext()) {
            if (it.next().getId() == registeredService.getId()) {
                it.remove();
                foundAndRemovedService = true;
            }
        }
        col.add(registeredService);

        saveListOfRegisteredServices(col);
        return registeredService;
    }

    @Override
    protected boolean deleteInternal(final RegisteredService registeredService) {
        logger.debug("Loading service definitions from resource [{}]", this.servicesConfigFile.getFilename());
        final List<RegisteredService> resolvedServices = super.loadServices();
        final RegisteredService regServiceToDelete = findServiceById(registeredService.getId());

        if (regServiceToDelete != null) {
            logger.debug("Found service definition to remove: [{}]", regServiceToDelete);
            final List<RegisteredService> col = new ArrayList<RegisteredService>(resolvedServices);
            col.remove(regServiceToDelete);

            saveListOfRegisteredServices(col);
            return true;
        }
        return false;
    }

    private void saveListOfRegisteredServices(final List<RegisteredService> col) {
        OutputStream out = null;
        FileOutputStream fout = null;

        try {
            fout = new FileOutputStream(this.servicesConfigFile.getFile());
            out = new BufferedOutputStream(fout);

            final Map<String, Object> map = new LinkedHashMap<String, Object>(col.size());
            map.put(SERVICES_KEY, col);

            logger.debug("Writing [{}] service definitions to resource [{}]", col.size(), this.servicesConfigFile.getFilename());
            this.objectMapper.writerWithDefaultPrettyPrinter().writeValue(out, map);

            fout.flush();
            out.flush();

        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(fout);
            IOUtils.closeQuietly(out);
        }
    }
}
