package com.plantshop.user.mapper;

import com.plantshop.user.client.OrderPrivacyClient.CustomerOrder;
import com.plantshop.user.dto.PersonalDataExport.ActivityEntry;
import com.plantshop.user.dto.PersonalDataExport.AddressEntry;
import com.plantshop.user.dto.PersonalDataExport.AnalyticsEntry;
import com.plantshop.user.dto.PersonalDataExport.CommunicationEntry;
import com.plantshop.user.dto.PersonalDataExport.LinkedAccount;
import com.plantshop.user.dto.PersonalDataExport.OrderEntry;
import com.plantshop.user.dto.PersonalDataExport.OrderLine;
import com.plantshop.user.dto.PersonalDataExport.PrivacyRequestEntry;
import com.plantshop.user.dto.PersonalDataExport.Profile;
import com.plantshop.user.dto.PersonalDataExport.Settings;
import com.plantshop.user.entity.Address;
import com.plantshop.user.entity.AnalyticsEvent;
import com.plantshop.user.entity.EmailMessage;
import com.plantshop.user.entity.PrivacyAuditEvent;
import com.plantshop.user.entity.User;
import com.plantshop.user.entity.UserActivity;
import com.plantshop.user.entity.UserSettings;

/**
 * Entity to export-section mapping. Minimization happens here: each method copies an explicit list of
 * fields, so a column added to an entity later is not exported until someone decides it should be.
 */
public final class PersonalDataMapper {

    private PersonalDataMapper() {
    }

    /** Not copied: password hash, verification and reset tokens, fraud review flag, role. */
    public static Profile toProfile(User user) {
        LinkedAccount linkedAccount = user.getExternalProvider() == null
                ? null
                : new LinkedAccount(user.getExternalProvider(), user.getExternalSubjectId());
        return new Profile(user.getFullName(), user.getEmail(), user.getPhone(), user.isEmailVerified(), linkedAccount,
                user.getCreatedAt(), user.getUpdatedAt());
    }

    public static AddressEntry toEntry(Address address) {
        return new AddressEntry(address.getLabel(), address.getRecipient(), address.getLine1(), address.getCity(),
                address.getPostalCode(), address.getCountry(), address.getCreatedAt());
    }

    public static Settings toSettings(UserSettings settings) {
        return new Settings(settings.getLanguage(), settings.getCurrency(), settings.isDarkMode(),
                settings.getUpdatedAt());
    }

    public static ActivityEntry toEntry(UserActivity activity) {
        return new ActivityEntry(activity.getType(), activity.getDetails(), activity.getIpAddress(),
                activity.getUserAgent(), activity.getOccurredAt());
    }

    public static CommunicationEntry toEntry(EmailMessage message) {
        return new CommunicationEntry(message.getCategory(), message.getTemplate(), message.getRecipient(),
                message.getCreatedAt());
    }

    public static AnalyticsEntry toEntry(AnalyticsEvent event) {
        return new AnalyticsEntry(event.getEventName(), event.getPage(), event.getOccurredAt());
    }

    /** The actor is left out: it identifies the employee who handled the request, not the subject. */
    public static PrivacyRequestEntry toEntry(PrivacyAuditEvent event) {
        return new PrivacyRequestEntry(event.getEventType(), event.getOutcome(), event.getOccurredAt());
    }

    public static OrderEntry toEntry(CustomerOrder order) {
        return new OrderEntry(order.orderId(), order.status(), order.paymentMethod(), order.totalAmount(),
                order.shippingAddress(), order.createdAt(),
                order.items().stream()
                        .map(i -> new OrderLine(i.plantId(), i.plantName(), i.quantity(), i.unitPrice()))
                        .toList());
    }
}
