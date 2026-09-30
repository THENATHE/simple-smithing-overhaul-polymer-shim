package sso.qa;

import java.util.*;
import com.mojang.authlib.GameProfile;
import me.pajic.simple_smithing_overhaul.SSO;
import me.pajic.simple_smithing_overhaul.blocks.ModBlocks;
import me.pajic.simple_smithing_overhaul.items.ModItems;
import me.pajic.simple_smithing_overhaul.recipe.PortableItemRepairRecipe;
import me.pajic.simple_smithing_overhaul.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.level.block.*;

/** Disposable server gameplay checks. Never install on a real server. */
public final class RepairChecks implements ModInitializer {
    private static int assertions;
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> check(s, "startup"));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((s,r,ok) -> { require(ok,"reload completed"); check(s,"reload"); });
    }
    private static void check(MinecraftServer s, String phase) {
        assertions=0;
        try {
            repairs(s); broken(s);
            var profile=new GameProfile(UUID.randomUUID(), "SSO_QA");
            var p=new ServerPlayer(s,s.overworld(),profile,ClientInformation.createDefault());
            p.connection=new ServerGamePacketListenerImpl(s,new Connection(PacketFlow.SERVERBOUND),p,CommonListenerCookie.createInitial(profile,false));
            p.experienceLevel=100;
            menus(s,p); mending(s,p); mendingSelection(s,p); enchantingAndCrafting(s,p); experienceAndTrades(s,p); smithing(s,p); data(s,p); polymer(s);
            System.out.println("SSO_QA_REPAIR_PASS " + phase + " assertions=" + assertions);
        } catch(Throwable t) { throw new AssertionError("SSO_QA_GAMEPLAY_FAIL " + phase,t); }
    }
    private static ItemStack damaged(Item i) { var s=new ItemStack(i); s.setDamageValue(s.getMaxDamage()*2/3); return s; }
    private static void repairs(MinecraftServer s) {
        Item[][] mappings={{Items.BOW,Items.STRING},{Items.CROSSBOW,Items.STRING},{Items.FLINT_AND_STEEL,Items.IRON_INGOT},{Items.SHEARS,Items.IRON_INGOT},{Items.BRUSH,Items.FEATHER},{Items.CARROT_ON_A_STICK,Items.CARROT},{Items.WARPED_FUNGUS_ON_A_STICK,Items.WARPED_FUNGUS},{Items.TRIDENT,Items.PRISMARINE_SHARD},{Items.FISHING_ROD,Items.STRING}};
        for(var m:mappings) require(ModUtil.isValidRepairItem(new ItemStack(m[0]),new ItemStack(m[1])),"repair material " + m[0]);
        var bow=damaged(Items.BOW); int before=bow.getDamageValue();
        var recipe=new PortableItemRepairRecipe();
        var in=CraftingInput.of(3,1,List.of(bow,new ItemStack(Items.STRING),new ItemStack(Items.FLINT)));
        require(recipe.matches(in,s.overworld()),"flint recipe");
        var result=recipe.assemble(in);
        require(result.getDamageValue()==Math.max(0,before-(int)Math.ceil((float)bow.getMaxDamage()/ModUtil.determineUnitCost(bow))),"portable repair exact amount");
        require(result.getOrDefault(ModDataComponents.REPAIR_COUNT,0)==1,"repair counter");
        require(bow.getDamageValue()==before,"input damage preserved");
        require(recipe.getRemainingItems(in).stream().allMatch(ItemStack::isEmpty),"flint/material consumed");
        require(!recipe.matches(CraftingInput.of(3,1,List.of(bow,new ItemStack(Items.DIAMOND),new ItemStack(Items.FLINT))),s.overworld()),"wrong repair material rejected");
        var unbreaking=s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        bow.enchant(unbreaking,1);
        require(!recipe.matches(CraftingInput.of(3,1,List.of(bow,new ItemStack(Items.STRING),new ItemStack(Items.FLINT))),s.overworld()),"flint rejects enchanted item");
        var stone=new ItemStack(ModItems.WHETSTONE);
        require(!recipe.matches(CraftingInput.of(3,1,List.of(bow,new ItemStack(Items.STRING),stone)),s.overworld()),"whetstone requires matching enchantments");
        EnchantmentHelper.updateEnchantments(stone,m->m.set(unbreaking,1));
        in=CraftingInput.of(3,1,List.of(bow,new ItemStack(Items.STRING),stone));
        require(recipe.matches(in,s.overworld()),"enchanted whetstone recipe");
        require(recipe.assemble(in).getEnchantments().getLevel(unbreaking)==1,"portable repair preserves enchantments");
        require(recipe.getRemainingItems(in).get(2).is(ModItems.WHETSTONE),"whetstone retained");
        stone.setDamageValue(stone.getMaxDamage());
        require(!recipe.matches(CraftingInput.of(3,1,List.of(bow,new ItemStack(Items.STRING),stone)),s.overworld()),"broken whetstone rejected");
    }
    private static void broken(MinecraftServer server) {
        var sword=new ItemStack(Items.IRON_SWORD); sword.setDamageValue(sword.getMaxDamage()-1);
        sword.hurtAndBreak(1,server.overworld(),(ServerPlayer)null,stack -> {});
        require(sword.getCount()==1,"durability exhaustion preserves item");
        require(ModUtil.isBroken(sword),"broken marker");
        int[] count={0}; sword.forEachModifier(EquipmentSlot.MAINHAND,(a,m)->count[0]++);
        require(count[0]==0,"broken gear contributes no attributes");
        sword.setDamageValue(0); require(!ModUtil.isBroken(sword),"repair clears broken marker");
        sword.forEachModifier(EquipmentSlot.MAINHAND,(a,m)->count[0]++); require(count[0]>0,"repaired attributes restored");
        require(AnvilBlock.damage(Blocks.DAMAGED_ANVIL.defaultBlockState()).is(ModBlocks.BROKEN_ANVIL),"damaged anvil becomes broken block");
    }
    private static void menus(MinecraftServer s,ServerPlayer p) {
        var pos=new BlockPos(0,200,0); s.overworld().setBlock(pos,Blocks.ANVIL.defaultBlockState(),2);
        var access=ContainerLevelAccess.create(s.overworld(),pos);
        var a=new AnvilMenu(1,p.getInventory(),access);
        var tool=damaged(Items.IRON_PICKAXE); a.getSlot(0).set(tool); a.getSlot(1).set(new ItemStack(Items.IRON_INGOT)); a.createResult();
        require(!a.getSlot(2).getItem().isEmpty(),"anvil repair output");
        require(a.getSlot(2).getItem().getDamageValue()<tool.getDamageValue(),"anvil repairs damage");
        p.experienceLevel=0; require(a.getSlot(2).mayPickup(p),"free unenchanted repair pickup at zero XP");
        int xp=p.experienceLevel; a.getSlot(2).onTake(p,a.getSlot(2).getItem().copy()); require(p.experienceLevel==xp,"unenchant repair free XP");
        a=new AnvilMenu(2,p.getInventory(),access); a.getSlot(0).set(new ItemStack(Items.IRON_SWORD)); a.setItemName("QA named blade"); a.createResult();
        require(a.getCost()==0 && !a.getSlot(2).getItem().isEmpty(),"free rename output");
        require(a.getSlot(2).mayPickup(p),"free rename pickup allowed");
        a=new AnvilMenu(20,p.getInventory(),access); var expensive=new ItemStack(Items.DIAMOND_SWORD); expensive.set(DataComponents.REPAIR_COST,100);
        var highBook=new ItemStack(Items.ENCHANTED_BOOK); EnchantmentHelper.updateEnchantments(highBook,e->e.set(s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS),3));
        a.getSlot(0).set(expensive); a.getSlot(1).set(highBook); a.createResult(); p.experienceLevel=200;
        require(a.getCost()>=40 && !a.getSlot(2).getItem().isEmpty() && a.getSlot(2).mayPickup(p),"anvil high-cost combination allowed above vanilla limit");
        s.overworld().setBlock(pos,ModBlocks.BROKEN_ANVIL.defaultBlockState(),2);
        a=new AnvilMenu(3,p.getInventory(),access); a.getSlot(0).set(damaged(Items.IRON_PICKAXE)); a.getSlot(1).set(new ItemStack(Items.IRON_INGOT)); a.createResult(); require(a.getSlot(2).getItem().isEmpty(),"broken anvil cannot repair");
        var iron=new ItemStack(Items.IRON_BLOCK,3);
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false);
        for(var target:List.of(Blocks.DAMAGED_ANVIL,Blocks.CHIPPED_ANVIL,Blocks.ANVIL)) {
            s.overworld().getBlockState(pos).useItemOn(iron,s.overworld(),p,InteractionHand.MAIN_HAND,hit);
            require(s.overworld().getBlockState(pos).is(target),"iron-block anvil repair " + target);
        }
        require(iron.isEmpty(),"anvil repair consumes one iron block per stage");
        var g=new GrindstoneMenu(4,p.getInventory()); tool=new ItemStack(Items.DIAMOND_SWORD); tool.set(DataComponents.REPAIR_COST,31);
        var sharp=s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS); tool.enchant(sharp,2);
        g.getSlot(0).set(tool); g.getSlot(1).set(new ItemStack(Items.NETHERITE_SCRAP));
        require(g.getSlot(2).getItem().getOrDefault(DataComponents.REPAIR_COST,0)==15,"grindstone halves prior work cost");
        require(g.getSlot(2).getItem().getEnchantments().getLevel(sharp)==2,"scrap preserves enchantment");
        g.getSlot(1).set(new ItemStack(Items.NETHERITE_SCRAP,2)); require(g.getSlot(2).getItem().isEmpty(),"multiple scrap rejected");
    }
    private static void mending(MinecraftServer s,ServerPlayer p) {
        p.getInventory().clearContent();
        var m=s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING);
        var tool=damaged(Items.DIAMOND_PICKAXE); tool.enchant(m,1); var stone=new ItemStack(ModItems.WHETSTONE); EnchantmentHelper.updateEnchantments(stone,e->e.set(m,1));
        p.getInventory().setItem(0,tool); p.getInventory().setItem(1,stone); p.getInventory().setItem(2,new ItemStack(Items.DIAMOND,2));
        int damage=tool.getDamageValue(); require(ModUtil.tryRepairItem(tool,p,s.overworld()),"mending repair succeeds");
        require(tool.getDamageValue()<damage,"mending repaired durability"); require(p.getInventory().getItem(2).getCount()==1,"mending consumes exactly one material");
        damage=tool.getDamageValue(); p.setItemInHand(InteractionHand.MAIN_HAND,tool); p.setShiftKeyDown(true);
        tool.use(s.overworld(),p,InteractionHand.MAIN_HAND); p.setShiftKeyDown(false);
        require(tool.getDamageValue()<damage,"shift use triggers mending repair");
        require(p.getInventory().getItem(2).isEmpty(),"shift repair consumes one material");
        p.getInventory().clearContent(); require(!ModUtil.tryRepairItem(tool,p,s.overworld()),"mending needs inventory materials");
        for(var item:List.of(Items.BOW,Items.CROSSBOW,Items.TRIDENT,Items.FISHING_ROD)) {
            p.getInventory().clearContent(); var target=damaged(item); target.enchant(m,1);
            stone=new ItemStack(ModItems.WHETSTONE); EnchantmentHelper.updateEnchantments(stone,e->e.set(m,1));
            p.setItemInHand(InteractionHand.MAIN_HAND,target); p.getInventory().setItem(1,stone); p.getInventory().setItem(2,new ItemStack(item==Items.TRIDENT?Items.PRISMARINE_SHARD:Items.STRING,2));
            p.setShiftKeyDown(true); damage=target.getDamageValue();
            net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker().interact(p,s.overworld(),InteractionHand.MAIN_HAND);
            require(target.getDamageValue()<damage,"server crouch repair for subclass " + item);
            require(target.getOrDefault(ModDataComponents.REPAIR_COUNT,0)==1,"manual mending repair counter " + item);
            require(p.getInventory().getItem(2).getCount()==1,"subclass mending consumes one material " + item);
        }
        p.getInventory().clearContent(); var bow=damaged(Items.BOW); bow.enchant(m,1); bow.setDamageValue(bow.getMaxDamage());
        stone=new ItemStack(ModItems.WHETSTONE); EnchantmentHelper.updateEnchantments(stone,e->e.set(m,1));
        p.setItemInHand(InteractionHand.MAIN_HAND,bow); p.getInventory().setItem(1,stone); p.getInventory().setItem(2,new ItemStack(Items.STRING,3));
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker().interact(p,s.overworld(),InteractionHand.MAIN_HAND);
        require(!ModUtil.isBroken(bow),"broken bow repaired before broken-use guard");
        damage=bow.getDamageValue(); p.setShiftKeyDown(false);
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker().interact(p,s.overworld(),InteractionHand.MAIN_HAND);
        require(bow.getDamageValue()==damage,"non-crouch leaves durability alone");
        p.setShiftKeyDown(true); SSO.CONFIG.mendingRework.enabled.accept(false);
        try { net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker().interact(p,s.overworld(),InteractionHand.MAIN_HAND); require(bow.getDamageValue()==damage,"disabled mending leaves durability alone"); }
        finally { SSO.CONFIG.mendingRework.enabled.accept(true); }
        p.getInventory().setItem(2,ItemStack.EMPTY); net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.invoker().interact(p,s.overworld(),InteractionHand.MAIN_HAND);
        require(bow.getDamageValue()==damage,"crouch repair requires materials"); p.setShiftKeyDown(false); p.getInventory().clearContent(); p.experienceLevel=100;
    }
    private static void mendingSelection(MinecraftServer s,ServerPlayer p) {
        p.getInventory().clearContent();
        var m=s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING);
        var target=damaged(Items.DIAMOND_PICKAXE); target.enchant(m,1);
        var bad=new ItemStack(ModItems.WHETSTONE); bad.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("incompatible")); var badBefore=bad.copy();
        var good=new ItemStack(ModItems.WHETSTONE); good.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("compatible")); EnchantmentHelper.updateEnchantments(good,e->e.set(m,1));
        var material=new ItemStack(Items.DIAMOND,3); material.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("repair diamond"));
        p.setItemInHand(InteractionHand.MAIN_HAND,target); p.getInventory().setItem(1,bad); p.getInventory().setItem(2,good); p.getInventory().setItem(3,material);
        require(ModUtil.tryRepairItem(target,p,s.overworld()),"mending skips incompatible earlier whetstone");
        require(ItemStack.isSameItemSameComponents(p.getInventory().getItem(1),badBefore),"incompatible stone untouched");
        require(p.getInventory().getItem(2).get(DataComponents.CUSTOM_NAME).getString().equals("compatible"),"selected whetstone keeps custom name");
        require(p.getInventory().getItem(3).getCount()==2 && p.getInventory().getItem(3).get(DataComponents.CUSTOM_NAME).getString().equals("repair diamond"),"material keeps custom name and consumes one");
        target.setDamageValue(target.getMaxDamage()); p.getInventory().tick();
        require(!ModUtil.isBroken(target),"inventory tick automatically repairs broken Mending tool");
        require(target.getOrDefault(ModDataComponents.REPAIR_COUNT,0)==2,"auto repair increments counter");
        p.getInventory().setItem(2,ItemStack.EMPTY); int damage=target.getDamageValue(); var materialBefore=p.getInventory().getItem(3).copy();
        require(!ModUtil.tryRepairItem(target,p,s.overworld()),"mending rejects all incompatible whetstones");
        require(target.getDamageValue()==damage && ItemStack.isSameItemSameComponents(p.getInventory().getItem(1),badBefore) && ItemStack.matches(p.getInventory().getItem(3),materialBefore),"failed selection changes no input");
        p.getInventory().clearContent();
    }
    private static void enchantingAndCrafting(MinecraftServer s,ServerPlayer p) {
        var pos=new BlockPos(8,200,8); s.overworld().setBlock(pos,Blocks.ENCHANTING_TABLE.defaultBlockState(),2);
        for(var offset:EnchantingTableBlock.BOOKSHELF_OFFSETS) s.overworld().setBlock(pos.offset(offset),Blocks.BOOKSHELF.defaultBlockState(),2);
        var menu=new EnchantmentMenu(8,p.getInventory(),ContainerLevelAccess.create(s.overworld(),pos));
        menu.getSlot(0).set(new ItemStack(ModItems.WHETSTONE)); menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI,64)); p.experienceLevel=100;
        require(menu.costs[2]>0,"whetstone enchanting offers available");
        var capped=menu.costs.clone();
        int shelves=0;
        for(var offset:EnchantingTableBlock.BOOKSHELF_OFFSETS) s.overworld().setBlock(pos.offset(offset),shelves++<SSO.CONFIG.enchantmentLimits.enchantingTablePowerLimit.get()?Blocks.BOOKSHELF.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
        menu.getSlot(0).set(new ItemStack(ModItems.WHETSTONE));
        require(Arrays.equals(capped,menu.costs),"extra bookshelves above cap don't raise costs: full="+Arrays.toString(capped)+" physical cap="+Arrays.toString(menu.costs));
        for(var offset:EnchantingTableBlock.BOOKSHELF_OFFSETS) s.overworld().setBlock(pos.offset(offset),Blocks.BOOKSHELF.defaultBlockState(),2);
        SSO.CONFIG.enchantmentLimits.limitEnchantingTablePower.accept(false);
        try { menu.getSlot(0).set(new ItemStack(ModItems.WHETSTONE)); require(menu.costs[2]==30,"disabling cap restores full enchanting power"); }
        finally { SSO.CONFIG.enchantmentLimits.limitEnchantingTablePower.accept(true); }
        menu.getSlot(0).set(new ItemStack(ModItems.WHETSTONE)); require(Arrays.equals(capped,menu.costs),"reenabling cap restores configured power");
        require(menu.clickMenuButton(p,2),"enchanting table accepts whetstone");
        require(!menu.getSlot(0).getItem().getOrDefault(DataComponents.STORED_ENCHANTMENTS,ItemEnchantments.EMPTY).isEmpty(),"table stores whetstone enchantments");
        var anvilPos=pos.offset(0,0,4); s.overworld().setBlock(anvilPos,Blocks.ANVIL.defaultBlockState(),2);
        var a=new AnvilMenu(9,p.getInventory(),ContainerLevelAccess.create(s.overworld(),anvilPos));
        var book=new ItemStack(Items.ENCHANTED_BOOK); var sharp=s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS); EnchantmentHelper.updateEnchantments(book,e->e.set(sharp,1));
        a.getSlot(0).set(new ItemStack(ModItems.WHETSTONE)); a.getSlot(1).set(book); a.createResult();
        require(!a.getSlot(2).getItem().isEmpty() && a.getSlot(2).getItem().getOrDefault(DataComponents.STORED_ENCHANTMENTS,ItemEnchantments.EMPTY).getLevel(sharp)==1,"anvil applies enchantment book to whetstone");
        var quartz=CraftingInput.of(3,2,List.of(new ItemStack(Items.QUARTZ),new ItemStack(Items.QUARTZ),new ItemStack(Items.QUARTZ),new ItemStack(Items.QUARTZ),new ItemStack(Items.QUARTZ),new ItemStack(Items.QUARTZ)));
        var recipe=s.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,quartz,s.overworld());
        require(recipe.isPresent() && recipe.get().value().assemble(quartz).is(ModItems.WHETSTONE),"quartz crafting recipe produces whetstone");
        for(var pair:List.of(new Item[]{ModItems.ENCHANTMENT_UPGRADE_SMITHING_TEMPLATE,Items.LAPIS_BLOCK},new Item[]{ModItems.PINNACLE_ENCHANTMENT_SMITHING_TEMPLATE,Items.SCULK_CATALYST})) {
            var grid=new ArrayList<ItemStack>(); for(int i=0;i<9;i++) grid.add(new ItemStack(Items.DIAMOND)); grid.set(1,new ItemStack(pair[0])); grid.set(4,new ItemStack(pair[1]));
            var input=CraftingInput.of(3,3,grid); recipe=s.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,s.overworld());
            require(recipe.isPresent(),"template duplication matches " + pair[0]); var out=recipe.get().value().assemble(input); require(out.is(pair[0]) && out.getCount()==2,"template duplication yields two " + pair[0]);
        }
        p.getInventory().clearContent(); p.experienceLevel=100;
    }
    private static void experienceAndTrades(MinecraftServer s,ServerPlayer p) throws Exception {
        var reg=s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT); var sharp=reg.getOrThrow(Enchantments.SHARPNESS);
        var book=new ItemStack(Items.ENCHANTED_BOOK); EnchantmentHelper.updateEnchantments(book,e->e.set(sharp,5));
        var trade=net.minecraft.world.item.trading.VillagerTrade.builder(new net.minecraft.world.item.trading.TradeCost(Items.EMERALD,20),ItemStackTemplate.fromNonEmptyStack(book),12,5,0.05f).build();
        var params=new net.minecraft.world.level.storage.loot.LootParams.Builder(s.overworld()).create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.EMPTY);
        var context=new net.minecraft.world.level.storage.loot.LootContext.Builder(params).create(Optional.empty());
        var offer=trade.getOffer(context);
        require(offer.getMaxUses()==SSO.CONFIG.enchantmentLimits.bookTradeUsesLimit.get(),"villager enchanted book trade use cap");
        require(offer.getResult().getOrDefault(DataComponents.STORED_ENCHANTMENTS,ItemEnchantments.EMPTY).getLevel(sharp)==SSO.CONFIG.enchantmentLimits.bookTradeLevelLimit.get(),"villager book level cap");
        var g=new GrindstoneMenu(10,p.getInventory()); var sword=new ItemStack(Items.DIAMOND_SWORD); sword.enchant(sharp,3); g.getSlot(0).set(sword);
        require(!g.getSlot(2).getItem().isEnchanted(),"grindstone removes enchantment");
        var xpMethod=g.getSlot(2).getClass().getDeclaredMethod("getExperienceAmount",net.minecraft.world.level.Level.class); xpMethod.setAccessible(true);
        int actual=(int)xpMethod.invoke(g.getSlot(2),s.overworld());
        int expected=ModUtil.calculateGrindstoneReward(sword.getEnchantments().entrySet().iterator().next());
        require(actual==expected,"grindstone awards increased deterministic XP");
        sword.set(DataComponents.REPAIR_COST,31); g.getSlot(0).set(sword); g.getSlot(1).set(new ItemStack(Items.NETHERITE_SCRAP));
        require((int)xpMethod.invoke(g.getSlot(2),s.overworld())==0,"scrap reduction yields no XP");
        var loc=new net.minecraft.world.phys.Vec3(8,201,8); var box=new net.minecraft.world.phys.AABB(6,199,6,10,205,10);
        for(var orb:s.overworld().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,box)) orb.discard();
        var bottle=new net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle(s.overworld(),loc.x,loc.y,loc.z,new ItemStack(Items.EXPERIENCE_BOTTLE));
        var hit=net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle.class.getDeclaredMethod("onHit",net.minecraft.world.phys.HitResult.class); hit.setAccessible(true);
        hit.invoke(bottle,new net.minecraft.world.phys.BlockHitResult(loc,net.minecraft.core.Direction.UP,BlockPos.containing(loc),false));
        int total=0; var count=net.minecraft.world.entity.ExperienceOrb.class.getDeclaredField("count"); count.setAccessible(true);
        for(var orb:s.overworld().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,box)) { total+=orb.getValue()*count.getInt(orb); orb.discard(); }
        require(total>=SSO.CONFIG.improvedExperienceBottle.minXp.get() && total<=SSO.CONFIG.improvedExperienceBottle.maxXp.get(),"bottle grants configured XP range: " + total);
    }
    private static void smithing(MinecraftServer s,ServerPlayer p) {
        var reg=s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT); var sharp=reg.getOrThrow(Enchantments.SHARPNESS); var unbreaking=reg.getOrThrow(Enchantments.UNBREAKING);
        var pos=new BlockPos(0,200,1); s.overworld().setBlock(pos,Blocks.SMITHING_TABLE.defaultBlockState(),2);
        var access=ContainerLevelAccess.create(s.overworld(),pos); var menu=new SmithingMenu(5,p.getInventory(),access);
        var book=new ItemStack(Items.ENCHANTED_BOOK); EnchantmentHelper.updateEnchantments(book,e->{e.set(sharp,1); e.set(unbreaking,1);});
        menu.getSlot(0).set(new ItemStack(ModItems.ENCHANTMENT_UPGRADE_SMITHING_TEMPLATE)); menu.getSlot(1).set(book); menu.getSlot(2).set(new ItemStack(Items.LAPIS_LAZULI)); menu.createResult();
        var first=menu.getSlot(3).getItem().copy(); require(!first.isEmpty(),"enchantment upgrade result"); require(((CostAccess)menu).sso$getCost()==5,"upgrade base XP cost");
        menu.getSlot(2).set(new ItemStack(Items.LAPIS_LAZULI,2)); menu.createResult(); var second=menu.getSlot(3).getItem();
        require(!second.isEmpty() && !ItemStack.isSameItemSameComponents(first,second),"lapis count selects different enchantment");
        p.experienceLevel=0; require(!menu.getSlot(3).mayPickup(p),"upgrade pickup blocked without XP"); p.experienceLevel=100;
        int xp=p.experienceLevel; var taken=menu.quickMoveStack(p,3); require(!taken.isEmpty(),"upgrade shift click succeeds"); require(p.experienceLevel==xp-5,"upgrade shift click pays exact XP");
        p.getInventory().clearContent();
        var pick=new ItemStack(Items.DIAMOND_PICKAXE); pick.enchant(reg.getOrThrow(Enchantments.EFFICIENCY),5); pick.enchant(reg.getOrThrow(Enchantments.FORTUNE),3); pick.enchant(unbreaking,3);
        menu=new SmithingMenu(6,p.getInventory(),access); menu.getSlot(0).set(new ItemStack(ModItems.PINNACLE_ENCHANTMENT_SMITHING_TEMPLATE)); menu.getSlot(1).set(pick); menu.getSlot(2).set(new ItemStack(Items.ECHO_SHARD)); menu.createResult();
        require(!menu.getSlot(3).getItem().isEmpty(),"maxed pickaxe pinnacle preview"); require(((CostAccess)menu).sso$getCost()==30,"pinnacle XP cost"); p.experienceLevel=0; require(!menu.getSlot(3).mayPickup(p),"pinnacle XP gate"); p.experienceLevel=100;
        taken=menu.quickMoveStack(p,3); require(!taken.isEmpty(),"pinnacle shift click");
        var upgraded=ModUtil.findItemOnPlayer(p,i->i.is(Items.DIAMOND_PICKAXE)).stack(); require(upgraded.getOrDefault(ModDataComponents.PINNACLE_COUNT,0)==1,"pinnacle applied exactly once"); require(p.experienceLevel==70,"pinnacle pays exact XP");
        require(upgraded.getEnchantments().entrySet().stream().filter(e->e.getIntValue()>e.getKey().value().getMaxLevel()).count()==1,"one pinnacle enchantment");
        var again=upgraded.copy(); p.getInventory().clearContent(); menu=new SmithingMenu(7,p.getInventory(),access); menu.getSlot(0).set(new ItemStack(ModItems.PINNACLE_ENCHANTMENT_SMITHING_TEMPLATE)); menu.getSlot(1).set(again); menu.getSlot(2).set(new ItemStack(Items.ECHO_SHARD)); menu.createResult();
        require(!menu.getSlot(3).getItem().isEmpty(),"pinnacle reroll preview"); require(((CostAccess)menu).sso$getCost()==35,"pinnacle reroll increased XP"); menu.quickMoveStack(p,3); upgraded=ModUtil.findItemOnPlayer(p,i->i.is(Items.DIAMOND_PICKAXE)).stack();
        require(upgraded.getOrDefault(ModDataComponents.PINNACLE_COUNT,0)==2,"pinnacle reroll count"); require(upgraded.getEnchantments().entrySet().stream().filter(e->e.getIntValue()>e.getKey().value().getMaxLevel()).count()==1,"reroll respects pinnacle limit"); require(p.experienceLevel==35,"reroll pays XP");
        p.getInventory().clearContent(); p.experienceLevel=100; menu=new SmithingMenu(21,p.getInventory(),access);
        menu.getSlot(0).set(new ItemStack(ModItems.PINNACLE_ENCHANTMENT_SMITHING_TEMPLATE)); var normalBase=new ItemStack(Items.DIAMOND_PICKAXE); normalBase.enchant(reg.getOrThrow(Enchantments.EFFICIENCY),5); normalBase.enchant(reg.getOrThrow(Enchantments.FORTUNE),3); normalBase.enchant(unbreaking,3); menu.getSlot(1).set(normalBase); menu.getSlot(2).set(new ItemStack(Items.ECHO_SHARD)); menu.createResult();
        require(!menu.getSlot(3).getItem().isEmpty(),"normal pinnacle preview"); menu.clicked(3,0,ContainerInput.PICKUP,p); var carried=menu.getCarried();
        require(carried.is(Items.DIAMOND_PICKAXE) && carried.getOrDefault(ModDataComponents.PINNACLE_COUNT,0)==1,"normal click applies pinnacle exactly once");
        require(p.experienceLevel==70,"normal pinnacle click pays exact XP");
        require(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty(),"normal pinnacle click consumes ingredients");
    }
    private static void data(MinecraftServer s,ServerPlayer p) {
        var manager=s.getAdvancements();
        for(String id:List.of("root","as_good_as_new","right_to_repair","pocket_smith","shiny_pockets","very_shiny","booksmith_blackworm","bad_rng","limit_break","all_maxed_out","one_step_at_a_time","hyperinflation","ship_of_theseus","disenchanter")) require(manager.get(SSO.id("smithing/"+id))!=null,"advancement loaded " + id);
        for(String id:List.of("as_good_as_new","one_step_at_a_time","limit_break")) require(p.getAdvancements().getOrStartProgress(manager.get(SSO.id("smithing/"+id))).isDone(),"gameplay granted advancement " + id);
        var ops=s.reloadableRegistries().lookup().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        for(String[] test:List.of(new String[]{"end_city_treasure","simple_smithing_overhaul:enchantment_upgrade"},new String[]{"ancient_city","simple_smithing_overhaul:pinnacle_enchantment"})) {
            var key=net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,net.minecraft.resources.Identifier.withDefaultNamespace("chests/"+test[0]));
            var table=s.reloadableRegistries().getLootTable(key);
            var json=net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.encodeStart(ops,table).getOrThrow().getAsJsonObject();
            boolean found=false;
            for(var e:json.getAsJsonArray("pools")) {
                var pool=e.getAsJsonObject(); if(!pool.toString().contains(test[1])) continue;
                require(pool.has("condition"),"template loot preserves chance condition " + test[0]);
                require(pool.get("condition").toString().contains("0.1"),"template loot retains ten-percent chance " + test[0]); found=true;
            }
            require(found,"template loot pool loaded " + test[0]);
        }
        var key=net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE,net.minecraft.resources.Identifier.withDefaultNamespace("chests/bastion_treasure"));
        String json=net.minecraft.world.level.storage.loot.LootTable.DIRECT_CODEC.encodeStart(ops,s.reloadableRegistries().getLootTable(key)).getOrThrow().toString();
        require(json.contains("minecraft:enchant_randomly"),"added book loot remains enchanted under 26.3 modifier format");
        require(json.contains("minecraft:set_count"),"loot count modifier retained");
    }
    private static void polymer(MinecraftServer s) {
        var mappings=List.of(new Item[]{ModItems.WHETSTONE,Items.FLINT},new Item[]{ModItems.BROKEN_ANVIL,Items.DAMAGED_ANVIL},new Item[]{ModItems.ENCHANTMENT_UPGRADE_SMITHING_TEMPLATE,Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE},new Item[]{ModItems.PINNACLE_ENCHANTMENT_SMITHING_TEMPLATE,Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE},new Item[]{ModItems.INFO_ENCHANTMENT_UPGRADE,Items.PAPER},new Item[]{ModItems.INFO_PINNACLE_ENCHANTMENT,Items.PAPER});
        for(var m:mappings) {
            var overlay=eu.pb4.polymer.core.api.utils.PolymerSyncedObject.getSyncedObject(BuiltInRegistries.ITEM,m[0]);
            require(overlay!=null,"Polymer item overlay " + m[0]);
            require(overlay.getPolymerReplacement(m[0],null)==m[1],"Polymer vanilla item fallback " + m[0]);
            var original=new ItemStack(m[0]); original.set(ModDataComponents.REPAIR_COUNT,7); var before=original.copy();
            var client=eu.pb4.polymer.core.api.item.PolymerItemUtils.getPolymerItemStack(original,null,s.registryAccess());
            require(client.is(m[1]),"client item stack mapped " + m[0]);
            require(ItemStack.isSameItemSameComponents(original,before),"network conversion preserves server stack " + m[0]);
            require(!client.has(ModDataComponents.REPAIR_COUNT),"custom component stripped from vanilla stack " + m[0]);
        }
        var block=eu.pb4.polymer.core.api.utils.PolymerSyncedObject.getSyncedObject(BuiltInRegistries.BLOCK,ModBlocks.BROKEN_ANVIL);
        require(block!=null,"Polymer broken anvil overlay");
        for(var c:List.of(ModDataComponents.REPAIR_COUNT,ModDataComponents.PINNACLE_COUNT,ModDataComponents.BROKEN)) require(eu.pb4.polymer.core.api.other.PolymerComponent.isPolymerComponent(c),"custom component server-only");
        require(eu.pb4.polymer.rsm.api.RegistrySyncUtils.isServerEntry(BuiltInRegistries.RECIPE_SERIALIZER,me.pajic.simple_smithing_overhaul.recipe.ModRecipeSerializers.PORTABLE_ITEM_REPAIR),"portable recipe serializer server-only");
    }
    private static void require(boolean condition,String label) { assertions++; if(!condition) throw new AssertionError("SSO_QA_ASSERT: " + label); }
}
