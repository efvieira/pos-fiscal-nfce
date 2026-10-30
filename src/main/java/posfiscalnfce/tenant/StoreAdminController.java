package posfiscalnfce.tenant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin/stores")
@Tag(name = "Lojas")
public class StoreAdminController {

    public record CreateStoreRequest(
            @NotBlank String name,
            @Pattern(regexp = "\\d{14}") String cnpj,
            @Pattern(regexp = "\\d{2,14}") String stateRegistration,
            @Pattern(regexp = "SP", message = "por enquanto só SP é suportado") String uf,
            @Pattern(regexp = "\\d{7}") String ibgeCityCode,
            @NotBlank String city,
            @Pattern(regexp = "\\d{1,6}") String cscId,
            @NotBlank String csc) {
    }

    public record StoreResponse(UUID id, String name, String cnpj, String uf, String city) {
        static StoreResponse from(Store s) {
            return new StoreResponse(s.getId(), s.getName(), s.getCnpj(), s.getUf(), s.getCity());
        }
    }

    private final StoreRepository stores;

    public StoreAdminController(StoreRepository stores) {
        this.stores = stores;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra uma loja emitente (tenant)")
    public StoreResponse create(@Valid @RequestBody CreateStoreRequest r) {
        return StoreResponse.from(stores.save(new Store(r.name(), r.cnpj(), r.stateRegistration(), r.uf(),
                r.ibgeCityCode(), r.city(), r.cscId(), r.csc())));
    }

    @GetMapping
    public List<StoreResponse> list() {
        return stores.findAll().stream().map(StoreResponse::from).toList();
    }
}
