package com.example.paymentadapter.provider.jsonplaceholder;

import com.example.paymentadapter.payment.PaymentAuthorisationRequest;
import com.example.paymentadapter.payment.PaymentAuthorisationResponse;
import com.example.paymentadapter.payment.PaymentProvider;
import com.example.paymentadapter.provider.jsonplaceholder.dto.CreatePostRequest;
import org.springframework.stereotype.Component;

/**
 * Simulated provider adapter.
 * <ol>
 *   <li>GET /posts/{merchantUserId} - stands in for a "merchant account lookup".</li>
 *   <li>POST /posts - stands in for "create authorisation"; the internal request is mapped to a post.</li>
 * </ol>
 */
@Component
public class JsonPlaceholderPaymentAdapter implements PaymentProvider {

    private final JsonPlaceholderHttpClient client;
    private final JsonPlaceholderProperties properties;

    public JsonPlaceholderPaymentAdapter(JsonPlaceholderHttpClient client, JsonPlaceholderProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public String name() {
        return JsonPlaceholderMapper.PROVIDER_NAME;
    }

    @Override
    public PaymentAuthorisationResponse authorise(PaymentAuthorisationRequest request) {
        int merchantUserId = properties.merchantUserId();

        JsonPlaceholderHttpClient.Result lookup = client.getPost(merchantUserId);
        JsonPlaceholderMapper.verifyMerchantLookup(lookup.body(), merchantUserId);

        CreatePostRequest providerRequest = JsonPlaceholderMapper.toProviderRequest(request, merchantUserId);
        JsonPlaceholderHttpClient.Result created = client.createPost(providerRequest);

        return JsonPlaceholderMapper.toInternalResponse(request, providerRequest, created.statusCode(), created.body());
    }
}
