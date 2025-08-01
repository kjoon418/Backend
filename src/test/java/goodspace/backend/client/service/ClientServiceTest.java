package goodspace.backend.client.service;

import goodspace.backend.admin.image.ImageManager;
import goodspace.backend.admin.image.ImageManagerImpl;
import goodspace.backend.client.domain.Client;
import goodspace.backend.client.domain.RegisterStatus;
import goodspace.backend.client.dto.ClientBriefInfoResponseDto;
import goodspace.backend.client.dto.ClientDetailsResponseDto;
import goodspace.backend.client.dto.ClientItemInfoResponseDto;
import goodspace.backend.client.dto.ItemBriefInfoResponseDto;
import goodspace.backend.client.repository.ClientRepository;
import goodspace.backend.fixture.ClientFixture;
import goodspace.backend.fixture.ImageFixture;
import goodspace.backend.fixture.ItemFixture;
import goodspace.backend.global.domain.Item;
import goodspace.backend.global.domain.ItemImage;
import goodspace.backend.global.repository.ItemRepository;
import goodspace.backend.testUtil.ImageUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ClientServiceTest {
    final String DEFAULT_TITLE_IMAGE = ImageFixture.JAVA.encodedImage;
    final String DEFAULT_TITLE_IMAGE_FILE_NAME = "title";

    @Autowired
    ClientService clientService;
    @Autowired
    ClientRepository clientRepository;
    @Autowired
    ItemRepository itemRepository;
    @Autowired
    ImageUtil imageUtil;

    @TempDir
    Path basePath;
    ImageManager imageManager;

    Client clientA;
    Client clientB;

    Client hasItemClient;
    Item itemA;
    Item itemB;
    Item privateItem;

    Set<Client> publicClients;
    Set<Item> publicItems;

    @BeforeEach
    void resetEntities() {
        imageManager = new ImageManagerImpl(basePath.toString());

        clientA = clientRepository.save(ClientFixture.INFLUENCER.getInstance());
        clientB = clientRepository.save(ClientFixture.CREATOR.getInstance());
        hasItemClient = clientRepository.save(ClientFixture.SINGER.getInstance());

        itemA = itemRepository.save(ItemFixture.PUBLIC_A.getInstance());
        itemB = itemRepository.save(ItemFixture.PUBLIC_B.getInstance());
        privateItem = itemRepository.save(ItemFixture.PRIVATE_A.getInstance());

        ItemImage titleImageA = ItemImage.from(imageManager.createImageUrl(itemA.getId(), DEFAULT_TITLE_IMAGE_FILE_NAME, DEFAULT_TITLE_IMAGE));
        ItemImage titleImageB = ItemImage.from(imageManager.createImageUrl(itemB.getId(), DEFAULT_TITLE_IMAGE_FILE_NAME, DEFAULT_TITLE_IMAGE));
        ItemImage titleImagePrivate = ItemImage.from(imageManager.createImageUrl(privateItem.getId(), DEFAULT_TITLE_IMAGE_FILE_NAME, DEFAULT_TITLE_IMAGE));

        itemA.setTitleImage(titleImageA);
        itemB.setTitleImage(titleImageB);
        privateItem.setTitleImage(titleImagePrivate);

        hasItemClient.addItem(itemA);
        hasItemClient.addItem(itemB);
        hasItemClient.addItem(privateItem);

        publicClients = Set.of(clientA, clientB, hasItemClient);
        publicItems = Set.of(itemA, itemB);
    }

    @Nested
    class getDetails {
        @Test
        @DisplayName("클라이언트의 상세 정보를 반환한다")
        void returnDetailsOfClient() {
            ClientDetailsResponseDto clientDetails = clientService.getDetails(clientA.getId());

            assertThat(isEqualWithoutItem(clientA, clientDetails)).isTrue();
        }

        @Test
        @DisplayName("클라이언트의 공개된 상품들을 반환한다")
        void returnItemsOfClient() throws IOException {
            ClientDetailsResponseDto clientDetails = clientService.getDetails(hasItemClient.getId());

            assertThat(isEqual(publicItems, clientDetails.items())).isTrue();

            for (ItemBriefInfoResponseDto itemDto : clientDetails.items()) {
                imageUtil.isSameImage(itemDto.titleImageUrl(), DEFAULT_TITLE_IMAGE);
            }
        }

        @Test
        @DisplayName("공개되지 않은 클라이언트라면 예외를 던진다")
        void ifNotReadyClientThenThrowException() {
            changeStatus(clientA, RegisterStatus.PRIVATE);

            assertThatThrownBy(() -> clientService.getDetails(clientA.getId()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class getClients {
        @Test
        @DisplayName("공개된 클라이언트의 목록을 반환한다")
        void returnClients() {
            List<ClientBriefInfoResponseDto> clientDtos = clientService.getPublicClients();

            assertThat(clientDtos.size() == publicClients.size()).isTrue();

            for (Client client : publicClients) {
                ClientBriefInfoResponseDto clientDto = clientDtos.stream()
                        .filter(dto -> dto.id().equals(client.getId()))
                        .findAny()
                        .orElseThrow(() -> new IllegalStateException("클라이언트와 아이디가 일치하는 DTO가 존재하지 않음"));

                assertThat(isEqual(client, clientDto)).isTrue();
            }
        }
    }

    @Nested
    class getClientAndItem {
        @Test
        @DisplayName("ID와 일치하는 클라이언트 정보를 반환한다")
        void returnClientInfoById() {
            ClientItemInfoResponseDto responseDto = clientService.getClientAndItem(hasItemClient.getId(), itemA.getId());

            assertThat(isEqual(hasItemClient, responseDto.client())).isTrue();
        }

        @Test
        @DisplayName("ID와 일치하는 상품 정보를 반환한다")
        void returnItemInfoById() {
            ClientItemInfoResponseDto responseDto = clientService.getClientAndItem(hasItemClient.getId(), itemA.getId());

            assertThat(isEqual(itemA, responseDto.item())).isTrue();
        }

        @Test
        @DisplayName("비공개 클라이언트라면 예외를 던진다")
        void ifPrivateClientThenThrowException() {
            changeStatus(hasItemClient, RegisterStatus.PRIVATE);

            assertThatThrownBy(() -> clientService.getClientAndItem(hasItemClient.getId(), itemA.getId()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("비공개 상품이라면 예외를 던진다")
        void ifPrivateItemThenThrowException() {
            assertThatThrownBy(() -> clientService.getClientAndItem(hasItemClient.getId(), privateItem.getId()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private boolean isEqualWithoutItem(Client client, ClientDetailsResponseDto dto) {
        return client.getName().equals(dto.name()) &&
                client.getProfileImageUrl().equals(dto.profileImageUrl()) &&
                client.getBackgroundImageUrl().equals(dto.backgroundImageUrl()) &&
                client.getIntroduction().equals(dto.introduction());
    }

    private boolean isEqual(Set<Item> items, List<ItemBriefInfoResponseDto> dtos) {
        Set<Long> itemIdSet = toSet(items, Item::getId);
        Set<Long> dtoIdSet = toSet(dtos, ItemBriefInfoResponseDto::id);

        Set<String> itemTitleImageUrlSet = toSet(items, Item::getTitleImageUrl);
        Set<String> dtoTitleImageUrlSet = toSet(dtos, ItemBriefInfoResponseDto::titleImageUrl);

        Set<String> itemNameSet = toSet(items, Item::getName);
        Set<String> dtoNameSet = toSet(dtos, ItemBriefInfoResponseDto::name);

        Set<String> itemDescriptionSet = toSet(items, Item::getLandingPageDescription);
        Set<String> dtoDescriptionSet = toSet(dtos, ItemBriefInfoResponseDto::landingPageDescription);

        Set<String> itemUrlSet = items.stream()
                .map(Item::getImageUrls)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        Set<String> dtoUrlSet = dtos.stream()
                .map(ItemBriefInfoResponseDto::imageUrls)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        return itemIdSet.equals(dtoIdSet) &&
                itemTitleImageUrlSet.equals(dtoTitleImageUrlSet) &&
                itemNameSet.equals(dtoNameSet) &&
                itemDescriptionSet.equals(dtoDescriptionSet) &&
                itemUrlSet.equals(dtoUrlSet);
    }

    private boolean isEqual(Client client, ClientBriefInfoResponseDto dto) {
        return client.getId().equals(dto.id()) &&
                client.getName().equals(dto.name()) &&
                client.getProfileImageUrl().equals(dto.profileImageUrl()) &&
                client.getClientType() == dto.clientType();
    }

    private boolean isEqual(Client client, ClientItemInfoResponseDto.ClientDto dto) {
        return client.getId().equals(dto.id()) &&
                client.getName().equals(dto.name()) &&
                client.getProfileImageUrl().equals(dto.profileImageUrl());
    }

    private boolean isEqual(Item item, ClientItemInfoResponseDto.ItemDto dto) {
        HashSet<String> itemImageUrlSet = new HashSet<>(item.getEveryImageUrl());
        HashSet<String> dtoImageUrlSet = new HashSet<>(dto.imageUrls());

        return item.getId().equals(dto.id()) &&
                item.getName().equals(dto.name()) &&
                item.getPrice().equals(dto.price()) &&
                item.getShortDescription().equals(dto.shortDescription()) &&
                item.getLandingPageDescription().equals(dto.landingPageDescription()) &&
                itemImageUrlSet.equals(dtoImageUrlSet);
    }

    private <E, T> Set<T> toSet(Collection<E> collection, Function<E, T> function) {
        return collection.stream()
                .map(function)
                .collect(Collectors.toSet());
    }

    private void changeStatus(Client client, RegisterStatus status) {
        client.update(
                client.getName(),
                client.getIntroduction(),
                client.getClientType(),
                status
        );
    }
}
