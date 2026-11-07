package com.ims.api.customer;

import com.ims.api.auth.AppUser;
import com.ims.api.auth.AppUserRepository;
import com.ims.api.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class CustomerAddressService {
    private static final int MAX_ADDRESSES_PER_CUSTOMER = 10;
    private final CustomerAddressRepository addresses;
    private final AppUserRepository users;

    public CustomerAddressService(CustomerAddressRepository addresses, AppUserRepository users) {
        this.addresses = addresses;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<CustomerDtos.AddressResponse> list(String email) {
        AppUser user = requireUser(email);
        return addresses.findAllByUser_IdOrderByDefaultAddressDescIdDesc(user.getId()).stream()
                .map(CustomerDtos.AddressResponse::from).toList();
    }

    @Transactional
    public CustomerDtos.AddressResponse create(String email, CustomerDtos.AddressRequest request) {
        AppUser user = lockUser(email);
        List<CustomerAddress> current = addresses.findAllByUser_IdOrderByDefaultAddressDescIdDesc(user.getId());
        if (current.size() >= MAX_ADDRESSES_PER_CUSTOMER) {
            throw new ApiException(HttpStatus.CONFLICT, "A customer can store up to ten delivery addresses");
        }
        boolean makeDefault = request.defaultAddress() || current.isEmpty();
        if (makeDefault) clearDefaults(current, null);
        CustomerAddress address = new CustomerAddress(user, clean(request.label()), clean(request.recipientName()),
                clean(request.phone()), clean(request.addressLine1()), clean(request.addressLine2()),
                clean(request.city()), clean(request.region()), clean(request.postalCode()),
                normalizeCountry(request.countryCode()), makeDefault);
        return CustomerDtos.AddressResponse.from(addresses.saveAndFlush(address));
    }

    @Transactional
    public CustomerDtos.AddressResponse update(long id, String email, CustomerDtos.AddressRequest request) {
        AppUser user = lockUser(email);
        CustomerAddress address = requireAddress(id, user.getId());
        List<CustomerAddress> current = addresses.findAllByUser_IdOrderByDefaultAddressDescIdDesc(user.getId());
        if (request.defaultAddress()) clearDefaults(current, id);
        address.update(clean(request.label()), clean(request.recipientName()), clean(request.phone()),
                clean(request.addressLine1()), clean(request.addressLine2()), clean(request.city()),
                clean(request.region()), clean(request.postalCode()), normalizeCountry(request.countryCode()));
        address.setDefaultAddress(request.defaultAddress());
        return CustomerDtos.AddressResponse.from(addresses.saveAndFlush(address));
    }

    @Transactional
    public void delete(long id, String email) {
        AppUser user = lockUser(email);
        CustomerAddress address = requireAddress(id, user.getId());
        List<CustomerAddress> current = addresses.findAllByUser_IdOrderByDefaultAddressDescIdDesc(user.getId());
        if (address.isDefaultAddress()) {
            current.stream().filter(candidate -> !candidate.getId().equals(id)).findFirst()
                    .ifPresent(candidate -> candidate.setDefaultAddress(true));
        }
        addresses.delete(address);
    }

    private void clearDefaults(List<CustomerAddress> addresses, Long exceptId) {
        for (CustomerAddress address : addresses) {
            if (!address.getId().equals(exceptId) && address.isDefaultAddress()) {
                address.setDefaultAddress(false);
            }
        }
    }

    private CustomerAddress requireAddress(long id, long userId) {
        return addresses.findByIdAndUser_Id(id, userId)
                .orElseThrow(() -> ApiException.notFound("Delivery address not found"));
    }

    private AppUser requireUser(String email) {
        return users.findByEmailIgnoreCase(email).orElseThrow(ApiException::unauthorized);
    }

    private AppUser lockUser(String email) {
        return users.findByEmailIgnoreCaseForUpdate(email).orElseThrow(ApiException::unauthorized);
    }

    private String normalizeCountry(String value) { return clean(value).toUpperCase(Locale.ROOT); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
