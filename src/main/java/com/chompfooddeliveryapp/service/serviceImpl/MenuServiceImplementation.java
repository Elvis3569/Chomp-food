package com.chompfooddeliveryapp.service.serviceImpl;

import com.chompfooddeliveryapp.dto.MenuItemDto;
import com.chompfooddeliveryapp.exception.BadRequestException;
import com.chompfooddeliveryapp.model.enums.MenuCategory;
import com.chompfooddeliveryapp.payload.MenuResponse;
import com.chompfooddeliveryapp.payload.UserFetchAllMealsResponse;
import com.chompfooddeliveryapp.repository.MenuItemRepository;
import com.chompfooddeliveryapp.service.serviceInterfaces.ImageService;
import com.chompfooddeliveryapp.service.serviceInterfaces.MenuItemService;
import com.chompfooddeliveryapp.exception.MenuNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import com.chompfooddeliveryapp.model.meals.MenuItem;


import javax.transaction.Transactional;
import java.io.IOException;
import java.util.List;

@Service
public class MenuServiceImplementation implements MenuItemService {

    private final MenuItemRepository menuItemRepository;



    public MenuServiceImplementation(MenuItemRepository menuItemRepository, ImageService imageService) {
        this.menuItemRepository = menuItemRepository;

    }


    @Override
    public MenuResponse addMenuItem(MenuItemDto menuItemDto) throws IOException {
        if(menuItemRepository.existsByName(menuItemDto.getName())){
            throw new BadRequestException("Menu already exists");
        }
        MenuItem menuItem = new MenuItem();
        menuItem.setName(menuItemDto.getName());
        menuItem.setCategory(menuItemDto.getCategory());
        menuItem.setPrice(menuItemDto.getPrice());
        menuItem.setDescription(menuItemDto.getDescription());
        menuItem.setImage(menuItemDto.getImage());
        MenuItem savedmenu = menuItemRepository.save(menuItem);
        return new MenuResponse("Menu item has been added", menuItem.getName(), menuItem.getPrice(),
                menuItem.getDescription(), menuItem.getCategory().name(), savedmenu.getImage());
    }

    @Override
    public MenuResponse updateMenuItem(Long id, MenuItemDto menuItemDto) throws IOException {

        MenuItem menuItem = getMenuItemById(id);
        if(menuItem == null){
            throw new BadRequestException("menu not found");
        }
        menuItem.setName(menuItemDto.getName());
        menuItem.setDescription(menuItemDto.getDescription());
        menuItem.setCategory(menuItemDto.getCategory());
        menuItem.setImage(menuItemDto.getImage());
        menuItem.setPrice(menuItemDto.getPrice());
        MenuItem updatedMenuitem = menuItemRepository.save(menuItem);
        return new MenuResponse("Menu item has been updated", menuItem.getName(), menuItem.getPrice(),
                menuItem.getDescription(), menuItem.getCategory().name(),updatedMenuitem.getImage());
    }

    @Override
    public MenuItem getMenuItemById(Long id) {
        return menuItemRepository.findMenuItemById(id).orElseThrow(()->new BadRequestException("Item " +  id + " was not found"));
    }

    @Override
    public List<MenuItem> getAllMenuItems() {
        return menuItemRepository.findAll();
    }

    @Override
    @Transactional
    public void deleteMenuItemById(Long id) {
        var menuItem1 = menuItemRepository.findById(id);
        if(menuItem1.isEmpty()){
            throw new MenuNotFoundException("menu not found");
        }
        menuItemRepository.deleteById(id);
    }
    @Override
    public UserFetchAllMealsResponse fetchAllMeals(Integer pageNo, String sortBy) {
       int pageSize = getAllMenuItems().size();
        return getUserFetchAllMealsResponse(pageNo, pageSize, sortBy, menuItemRepository);
    }

    private UserFetchAllMealsResponse getUserFetchAllMealsResponse(Integer pageNo, Integer pageSize, String sortBy, MenuItemRepository menuItemRepository) {
        Pageable paging = PageRequest.of(pageNo, pageSize, Sort.by(sortBy));
        Page<MenuItem> menuItems = menuItemRepository.findAll(paging);
        if(!menuItems.hasContent()) return new UserFetchAllMealsResponse("No menu Item", menuItems.getContent());
        return new UserFetchAllMealsResponse("Success", menuItems.getContent());
    }

    @Override
    public UserFetchAllMealsResponse fetchMealsByKeyWord(String keyword, int pageNo, int pageSize) {
        Page<MenuItem> menuItems = menuItemRepository.findAllByNameContains(keyword, PageRequest.of(pageNo, pageSize));
        if (!menuItems.hasContent()) return new UserFetchAllMealsResponse("No match found", menuItems.getContent());
        return new UserFetchAllMealsResponse("Match found", menuItems.getContent());
    }

    @Override
    public UserFetchAllMealsResponse fetchMealsByCategory(MenuCategory category, int pageNo, int pageSize) {
        Page<MenuItem> menuItems = menuItemRepository.findAllByCategory(category, PageRequest.of(pageNo, pageSize));
        if (!menuItems.hasContent()) return new UserFetchAllMealsResponse("Category does not exist or No items in Category", menuItems.getContent());
        return new UserFetchAllMealsResponse("Success", menuItems.getContent());
    }
}

